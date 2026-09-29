package com.zero.service;

import com.zero.domain.Account;
import com.zero.domain.Snapshot;
import com.zero.domain.SnapshotItem;
import com.zero.mapper.AccountMapper;
import com.zero.mapper.SnapshotMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

  private final SnapshotMapper snapshotMapper;
  private final AccountMapper accountMapper;
  private final FxRateService fxRateService;

  public DashboardService(
      SnapshotMapper snapshotMapper, AccountMapper accountMapper, FxRateService fxRateService) {
    this.snapshotMapper = snapshotMapper;
    this.accountMapper = accountMapper;
    this.fxRateService = fxRateService;
  }

  public Map<String, Object> summary(String userId) {
    List<Snapshot> desc = snapshotMapper.listSnapshotsByUser(userId);
    if (desc.isEmpty()) {
      return Map.of(
          "netWorth", 0.0,
          "monthlyChange", 0.0,
          "annualChange", 0.0,
          "annualizedReturn", 0.0);
    }
    Map<String, Account> accounts = accountsById(userId);
    FxTable fx = fxRateService.loadTable();
    Snapshot latest = desc.get(0);
    double nw = nwForSnapshot(latest, accounts, fx);

    LocalDate latestDate = LocalDate.parse(latest.getDate());
    LocalDate monthAgo = latestDate.minusMonths(1);
    double nwMonthAgo = nwLatestOnOrBefore(desc, monthAgo, accounts, fx);

    LocalDate yearAgo = latestDate.minusYears(1);
    double nwYearAgo = nwLatestOnOrBefore(desc, yearAgo, accounts, fx);

    return Map.of(
        "netWorth", nw,
        "monthlyChange", nw - nwMonthAgo,
        "annualChange", nw - nwYearAgo,
        "annualizedReturn", annualizedReturn(desc, accounts, fx));
  }

  public Map<String, Object> trend(String userId, String range) {
    List<Snapshot> desc = snapshotMapper.listSnapshotsByUser(userId);
    Map<String, Account> accounts = accountsById(userId);
    FxTable fx = fxRateService.loadTable();
    LocalDate from = rangeFromOrNull(range == null ? "all" : range);
    List<Map<String, Object>> points = new ArrayList<>();
    for (int i = desc.size() - 1; i >= 0; i--) {
      Snapshot s = desc.get(i);
      LocalDate d = LocalDate.parse(s.getDate());
      if (from != null && d.isBefore(from)) {
        continue;
      }
      double nw = nwForSnapshot(s, accounts, fx);
      Map<String, Object> pt = new LinkedHashMap<>();
      pt.put("date", s.getDate());
      pt.put("netWorth", nw);
      points.add(pt);
    }
    return Map.of("points", points);
  }

  public Map<String, Object> stackedByType(String userId, String range) {
    List<Snapshot> desc = snapshotMapper.listSnapshotsByUser(userId);
    if (desc.isEmpty()) {
      return Map.of("points", List.of());
    }
    Map<String, Account> accounts = accountsById(userId);
    FxTable fx = fxRateService.loadTable();
    LocalDate from = rangeFromOrNull(range == null ? "all" : range);
    List<Map<String, Object>> points = new ArrayList<>();
    for (int i = desc.size() - 1; i >= 0; i--) {
      Snapshot s = desc.get(i);
      LocalDate d = LocalDate.parse(s.getDate());
      if (from != null && d.isBefore(from)) {
        continue;
      }
      Map<String, Double> byType = compositionByTypeForSnapshot(s, accounts, fx);
      Map<String, Object> pt = new LinkedHashMap<>();
      pt.put("date", s.getDate());
      pt.put("byType", byType);
      points.add(pt);
    }
    return Map.of("points", points);
  }

  public Map<String, Object> accountTrends(String userId, String range) {
    List<Snapshot> desc = snapshotMapper.listSnapshotsByUser(userId);
    List<Account> active = accountMapper.listActiveByUser(userId);
    if (desc.isEmpty() || active.isEmpty()) {
      return Map.of("accounts", List.of());
    }
    FxTable fx = fxRateService.loadTable();
    LocalDate from = rangeFromOrNull(range == null ? "all" : range);
    List<Snapshot> inRange = new ArrayList<>();
    for (int i = desc.size() - 1; i >= 0; i--) {
      Snapshot s = desc.get(i);
      LocalDate d = LocalDate.parse(s.getDate());
      if (from != null && d.isBefore(from)) {
        continue;
      }
      inRange.add(s);
    }
    List<Map<String, Object>> accountRows = new ArrayList<>();
    for (Account acc : active) {
      SnapshotItem last = null;
      List<Map<String, Object>> pts = new ArrayList<>();
      for (Snapshot s : inRange) {
        SnapshotItem item = findItemForAccount(s.getId(), acc.getId());
        if (item != null) {
          last = item;
        }
        if (last == null) {
          continue;
        }
        double eff = BalanceLogic.baseBalance(acc.getType(), last, fx, s.getDate());
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("date", s.getDate());
        p.put("balance", eff);
        pts.add(p);
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("accountId", acc.getId());
      row.put("name", acc.getName());
      row.put("type", acc.getType());
      row.put("points", pts);
      accountRows.add(row);
    }
    return Map.of("accounts", accountRows);
  }

  public Map<String, Object> composition(String userId) {
    List<Snapshot> desc = snapshotMapper.listSnapshotsByUser(userId);
    if (desc.isEmpty()) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("byType", Map.of());
      empty.put("byOwner", Map.of());
      empty.put("byTypeAccounts", Map.of());
      return empty;
    }
    Map<String, Account> accounts = accountsById(userId);
    FxTable fx = fxRateService.loadTable();
    Snapshot latest = desc.get(0);
    List<SnapshotItem> items = snapshotMapper.listItems(latest.getId());
    Map<String, Double> byType = new HashMap<>();
    Map<String, Double> byOwner = new HashMap<>();
    Map<String, Map<String, Double>> byTypeAccounts = new LinkedHashMap<>();
    for (SnapshotItem it : items) {
      Account a = accounts.get(it.getAccountId());
      if (a == null) {
        continue;
      }
      double eff = BalanceLogic.baseBalance(a.getType(), it, fx, latest.getDate());
      byType.merge(a.getType(), eff, Double::sum);
      byOwner.merge(a.getOwner(), eff, Double::sum);
      byTypeAccounts
          .computeIfAbsent(a.getType(), k -> new LinkedHashMap<>())
          .merge(it.getAccountId(), eff, Double::sum);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("byType", byType);
    out.put("byOwner", byOwner);
    out.put("byTypeAccounts", byTypeAccounts);
    return out;
  }

  public Map<String, Object> typeChange(String userId) {
    List<Snapshot> desc = snapshotMapper.listSnapshotsByUser(userId);
    if (desc.size() < 2) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("latestDate", desc.isEmpty() ? null : desc.get(0).getDate());
      empty.put("previousDate", null);
      empty.put("items", List.of());
      return empty;
    }
    Map<String, Account> accounts = accountsById(userId);
    FxTable fx = fxRateService.loadTable();
    Snapshot latest = desc.get(0);
    Snapshot previous = desc.get(1);
    Map<String, Double> latestByType = compositionByTypeForSnapshot(latest, accounts, fx);
    Map<String, Double> previousByType = compositionByTypeForSnapshot(previous, accounts, fx);
    List<String> types = new ArrayList<>();
    for (String type : latestByType.keySet()) {
      if (!types.contains(type)) {
        types.add(type);
      }
    }
    for (String type : previousByType.keySet()) {
      if (!types.contains(type)) {
        types.add(type);
      }
    }
    types.sort(String::compareTo);

    List<Map<String, Object>> items = new ArrayList<>();
    for (String type : types) {
      double latestValue = latestByType.getOrDefault(type, 0.0);
      double previousValue = previousByType.getOrDefault(type, 0.0);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("type", type);
      row.put("latest", latestValue);
      row.put("previous", previousValue);
      row.put("change", latestValue - previousValue);
      items.add(row);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("latestDate", latest.getDate());
    out.put("previousDate", previous.getDate());
    out.put("items", items);
    return out;
  }

  public Map<String, Object> monthlyGrowth(String userId, Integer year) {
    int y = year != null ? year : LocalDate.now().getYear();
    List<Snapshot> desc = snapshotMapper.listSnapshotsByUser(userId);
    Map<String, Account> accounts = accountsById(userId);
    FxTable fx = fxRateService.loadTable();
    List<Map<String, Object>> points = new ArrayList<>();
    double cumulative = 0;
    for (int m = 1; m <= 12; m++) {
      YearMonth ym = YearMonth.of(y, m);
      LocalDate end = ym.atEndOfMonth();
      LocalDate prevEnd = ym.minusMonths(1).atEndOfMonth();
      double nwEnd = nwLatestOnOrBefore(desc, end, accounts, fx);
      double nwPrev = nwLatestOnOrBefore(desc, prevEnd, accounts, fx);
      double change = nwEnd - nwPrev;
      cumulative += change;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("month", String.format("%02d", m));
      row.put("change", change);
      row.put("cumulativeChange", cumulative);
      points.add(row);
    }
    return Map.of("year", y, "points", points);
  }

  private static LocalDate rangeFromOrNull(String range) {
    return switch (range) {
      case "3m" -> LocalDate.now().minusMonths(3);
      case "6m" -> LocalDate.now().minusMonths(6);
      case "1y" -> LocalDate.now().minusYears(1);
      default -> null;
    };
  }

  private Map<String, Double> compositionByTypeForSnapshot(
      Snapshot snapshot, Map<String, Account> accounts, FxTable fx) {
    List<SnapshotItem> items = snapshotMapper.listItems(snapshot.getId());
    Map<String, Double> byType = new HashMap<>();
    for (SnapshotItem it : items) {
      Account a = accounts.get(it.getAccountId());
      if (a == null) {
        continue;
      }
      double eff = BalanceLogic.baseBalance(a.getType(), it, fx, snapshot.getDate());
      byType.merge(a.getType(), eff, Double::sum);
    }
    return byType;
  }

  private SnapshotItem findItemForAccount(String snapshotId, String accountId) {
    for (SnapshotItem it : snapshotMapper.listItems(snapshotId)) {
      if (accountId.equals(it.getAccountId())) {
        return it;
      }
    }
    return null;
  }

  private double annualizedReturn(List<Snapshot> desc, Map<String, Account> accounts, FxTable fx) {
    if (desc.size() < 2) {
      return 0.0;
    }
    Snapshot latest = desc.get(0);
    Snapshot earliest = desc.get(desc.size() - 1);
    LocalDate dLatest = LocalDate.parse(latest.getDate());
    LocalDate dEarliest = LocalDate.parse(earliest.getDate());
    long days = ChronoUnit.DAYS.between(dEarliest, dLatest);
    if (days <= 0) {
      return 0.0;
    }
    double nwL = nwForSnapshot(latest, accounts, fx);
    double nwE = nwForSnapshot(earliest, accounts, fx);
    if (nwE <= 0) {
      return 0.0;
    }
    return Math.pow(nwL / nwE, 365.0 / days) - 1.0;
  }

  private double nwForSnapshot(Snapshot snapshot, Map<String, Account> accounts, FxTable fx) {
    List<SnapshotItem> items = snapshotMapper.listItems(snapshot.getId());
    return BalanceLogic.netWorth(items, accounts, fx, snapshot.getDate());
  }

  private double nwLatestOnOrBefore(
      List<Snapshot> desc, LocalDate target, Map<String, Account> accounts, FxTable fx) {
    for (Snapshot s : desc) {
      LocalDate d = LocalDate.parse(s.getDate());
      if (!d.isAfter(target)) {
        return nwForSnapshot(s, accounts, fx);
      }
    }
    return 0.0;
  }

  private Map<String, Account> accountsById(String userId) {
    return accountMapper.listAllByUser(userId).stream()
        .collect(Collectors.toMap(Account::getId, Function.identity()));
  }
}
