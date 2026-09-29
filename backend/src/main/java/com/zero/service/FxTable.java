package com.zero.service;

import com.zero.domain.Currency;
import com.zero.domain.FxRate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 一次请求内的汇率快照：按日期取当天或之前最近一天的汇率换算成本币 */
public final class FxTable {

  private final Map<Currency, TreeMap<String, Double>> byCurrency = new EnumMap<>(Currency.class);

  public FxTable(List<FxRate> rates) {
    for (FxRate r : rates) {
      byCurrency.computeIfAbsent(r.getCurrency(), k -> new TreeMap<>()).put(r.getRateDate(), r.getRate());
    }
  }

  /** 1 单位 currency 在 date 当天折合多少本币；没有记录时返回 null */
  public Double rateOrNull(Currency currency, String date) {
    if (currency == null || currency.isBase()) {
      return 1.0;
    }
    TreeMap<String, Double> series = byCurrency.get(currency);
    if (series == null || series.isEmpty()) {
      return null;
    }
    Map.Entry<String, Double> e = series.floorEntry(date);
    if (e == null) {
      // 快照早于所有已知汇率时，退而使用最早的一条
      e = series.firstEntry();
    }
    return e.getValue();
  }

  /** 保存快照时已保证外币汇率存在，缺失时按 0 计入，避免看板整体报错 */
  public double rate(Currency currency, String date) {
    Double r = rateOrNull(currency, date);
    return r == null ? 0.0 : r;
  }
}
