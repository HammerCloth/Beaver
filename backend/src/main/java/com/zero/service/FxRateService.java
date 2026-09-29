package com.zero.service;

import com.zero.domain.Currency;
import com.zero.domain.FxProvider;
import com.zero.domain.FxRate;
import com.zero.domain.FxRateSource;
import com.zero.mapper.FxRateMapper;
import com.zero.service.FxRemoteClient.Fetched;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FxRateService {

  /** 当天的汇率仍在变动，超过该时长重新拉取 */
  static final Duration TODAY_TTL = Duration.ofHours(1);

  private static final Logger log = LoggerFactory.getLogger(FxRateService.class);

  /**
   * @param rateDate 汇率实际所属日期；fallback 为 true 时表示接口不可用，取的是更早日期的已存汇率
   */
  public record Quote(
      Currency currency,
      String date,
      Double rate,
      FxRateSource source,
      FxProvider provider,
      String rateDate,
      boolean fallback) {}

  private final FxRateMapper fxRateMapper;
  private final FxRemoteClient remoteClient;

  public FxRateService(FxRateMapper fxRateMapper, FxRemoteClient remoteClient) {
    this.fxRateMapper = fxRateMapper;
    this.remoteClient = remoteClient;
  }

  public FxTable loadTable() {
    return new FxTable(fxRateMapper.listAll());
  }

  /** 查询某一天的汇率：优先本地记录，必要时联网获取并保存；失败时回退到之前最近一天 */
  public Quote quote(Currency currency, String date) {
    LocalDate d = parseDate(date);
    if (currency.isBase()) {
      return new Quote(currency, date, 1.0, null, null, date, false);
    }
    LocalDate today = today();
    boolean isCurrent = !d.isBefore(today);
    FxRate stored = fxRateMapper.find(currency, date);
    if (stored != null
        && (stored.getSource() == FxRateSource.MANUAL
            || (stored.getSource() == FxRateSource.API && (!isCurrent || isFresh(stored))))) {
      return toQuote(stored, date, false);
    }
    try {
      Fetched fetched = fetchRemote(currency, isCurrent ? null : d);
      FxRate row = new FxRate();
      row.setCurrency(currency);
      row.setRateDate(date);
      row.setRate(fetched.rate());
      row.setSource(FxRateSource.API);
      row.setProvider(fetched.provider());
      row.setFetchedAt(now().toString());
      fxRateMapper.upsert(row);
      return toQuote(row, date, false);
    } catch (Exception e) {
      log.warn("获取汇率失败 currency={} date={}: {}", currency, date, e.toString());
    }
    if (stored != null) {
      return toQuote(stored, date, stored.getSource() == FxRateSource.FALLBACK);
    }
    FxRate earlier = fxRateMapper.findLatestOnOrBefore(currency, date);
    if (earlier != null) {
      return toQuote(earlier, date, true);
    }
    return new Quote(currency, date, null, null, null, null, false);
  }

  /**
   * 保存快照前确保用到的外币在该日期有汇率：provided 只包含用户手动修改过的汇率，按手动记录保存；
   * 其余自动获取，接口不可用时把沿用的更早汇率记到该日期（FALLBACK），使快照金额不随之后录入的汇率漂移。
   */
  public void ensureRatesForSnapshot(String date, Set<Currency> used, Map<Currency, Double> provided) {
    for (Currency c : used) {
      if (c.isBase()) {
        continue;
      }
      Double p = provided == null ? null : provided.get(c);
      if (p != null) {
        if (!Double.isFinite(p) || p <= 0) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "汇率不合法");
        }
        FxRate stored = fxRateMapper.find(c, date);
        if (stored == null || Math.abs(stored.getRate() - p) > 1e-9) {
          FxRate row = new FxRate();
          row.setCurrency(c);
          row.setRateDate(date);
          row.setRate(p);
          row.setSource(FxRateSource.MANUAL);
          row.setFetchedAt(now().toString());
          fxRateMapper.upsert(row);
        }
        continue;
      }
      Quote q = quote(c, date);
      if (q.rate() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法获取 " + c + " 汇率，请手动填写");
      }
      if (q.fallback() && !date.equals(q.rateDate())) {
        FxRate row = new FxRate();
        row.setCurrency(c);
        row.setRateDate(date);
        row.setRate(q.rate());
        row.setSource(FxRateSource.FALLBACK);
        row.setProvider(q.provider());
        row.setFetchedAt(now().toString());
        fxRateMapper.upsert(row);
      }
    }
  }

  /** 逐级尝试各汇率源；date 为 null 表示最新 */
  protected Fetched fetchRemote(Currency currency, LocalDate date) {
    return remoteClient.fetch(currency, date);
  }

  protected LocalDate today() {
    return LocalDate.now();
  }

  protected Instant now() {
    return Instant.now();
  }

  private boolean isFresh(FxRate stored) {
    try {
      return Instant.parse(stored.getFetchedAt()).isAfter(now().minus(TODAY_TTL));
    } catch (Exception e) {
      return false;
    }
  }

  private static Quote toQuote(FxRate r, String date, boolean fallback) {
    return new Quote(r.getCurrency(), date, r.getRate(), r.getSource(), r.getProvider(), r.getRateDate(), fallback);
  }

  private static LocalDate parseDate(String date) {
    try {
      return LocalDate.parse(date);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数不合法");
    }
  }
}
