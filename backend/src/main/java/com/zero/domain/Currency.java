package com.zero.domain;

import java.util.Arrays;
import java.util.List;

/** 支持的币种，code 为 ISO 4217 数字码；本币为 CNY */
public enum Currency implements CodedEnum {
  CNY(156, "¥"),
  USD(840, "$"),
  HKD(344, "HK$");

  public static final Currency BASE = CNY;

  private final int code;
  private final String symbol;

  Currency(int code, String symbol) {
    this.code = code;
    this.symbol = symbol;
  }

  @Override
  public int code() {
    return code;
  }

  public String symbol() {
    return symbol;
  }

  public boolean isBase() {
    return this == BASE;
  }

  /** 需要换算成本币的外币 */
  public static List<Currency> foreign() {
    return Arrays.stream(values()).filter(c -> !c.isBase()).toList();
  }
}
