package com.zero.domain;

import java.time.LocalDate;

/** 汇率数据源，按声明顺序逐级尝试 */
public enum FxProvider implements CodedEnum {
  /** 欧洲央行参考汇率，支持 1999 年至今任意日期（非工作日取前一工作日） */
  FRANKFURTER(1, "欧洲央行", LocalDate.of(1999, 1, 4)),
  /** fawazahmed0/currency-api，jsDelivr CDN，历史数据自 2024-03 起 */
  CURRENCY_API_JSDELIVR(2, "currency-api", LocalDate.of(2024, 3, 2)),
  /** 同上数据的 Cloudflare Pages 镜像 */
  CURRENCY_API_CLOUDFLARE(3, "currency-api 镜像", LocalDate.of(2024, 3, 2)),
  /** open.er-api.com，仅提供最新汇率 */
  EXCHANGE_RATE_API(4, "ExchangeRate-API", null);

  private final int code;
  private final String label;
  /** 可查询的最早历史日期；null 表示只提供最新汇率 */
  private final LocalDate historicalFrom;

  FxProvider(int code, String label, LocalDate historicalFrom) {
    this.code = code;
    this.label = label;
    this.historicalFrom = historicalFrom;
  }

  @Override
  public int code() {
    return code;
  }

  public String label() {
    return label;
  }

  /** date 为 null 表示最新汇率，所有数据源都支持 */
  public boolean supports(LocalDate date) {
    return date == null || (historicalFrom != null && !date.isBefore(historicalFrom));
  }
}
