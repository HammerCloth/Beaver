package com.zero.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zero.domain.Account;
import com.zero.domain.Currency;
import com.zero.domain.FxRate;
import com.zero.domain.FxRateSource;
import com.zero.domain.SnapshotItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FxTableTest {

  private static FxRate rate(Currency c, String date, double r) {
    FxRate row = new FxRate();
    row.setCurrency(c);
    row.setRateDate(date);
    row.setRate(r);
    row.setSource(FxRateSource.API);
    row.setFetchedAt("2026-01-01T00:00:00Z");
    return row;
  }

  private final FxTable fx =
      new FxTable(
          List.of(
              rate(Currency.USD, "2026-09-01", 7.0),
              rate(Currency.USD, "2026-09-10", 7.1),
              rate(Currency.HKD, "2026-09-10", 0.9)));

  @Test
  void baseCurrencyIsAlwaysOne() {
    assertEquals(1.0, fx.rate(Currency.CNY, "2020-01-01"));
    assertEquals(1.0, fx.rate(null, "2020-01-01"));
  }

  @Test
  void usesSameDayOrClosestEarlierRate() {
    assertEquals(7.1, fx.rate(Currency.USD, "2026-09-10"));
    assertEquals(7.0, fx.rate(Currency.USD, "2026-09-05"));
    assertEquals(7.1, fx.rate(Currency.USD, "2026-12-31"));
  }

  @Test
  void fallsBackToEarliestWhenSnapshotPrecedesAllRates() {
    assertEquals(7.0, fx.rate(Currency.USD, "2025-01-01"));
  }

  @Test
  void missingCurrencyReturnsNull() {
    FxTable empty = new FxTable(List.of());
    assertNull(empty.rateOrNull(Currency.USD, "2026-09-10"));
    assertEquals(0.0, empty.rate(Currency.USD, "2026-09-10"));
  }

  @Test
  void netWorthConvertsForeignBalancesAndNegatesCredit() {
    Account cash = account("a1", "cash");
    Account card = account("a2", "credit");
    Account hk = account("a3", "deposit");
    List<SnapshotItem> items =
        List.of(item("a1", 1000, Currency.CNY), item("a2", -100, Currency.USD), item("a3", 1000, Currency.HKD));
    double nw = BalanceLogic.netWorth(items, Map.of("a1", cash, "a2", card, "a3", hk), fx, "2026-09-10");
    assertEquals(1000 - 710 + 900, nw, 1e-9);
  }

  private static Account account(String id, String type) {
    Account a = new Account();
    a.setId(id);
    a.setType(type);
    return a;
  }

  private static SnapshotItem item(String accountId, double balance, Currency c) {
    SnapshotItem it = new SnapshotItem();
    it.setAccountId(accountId);
    it.setBalance(balance);
    it.setCurrency(c);
    return it;
  }
}
