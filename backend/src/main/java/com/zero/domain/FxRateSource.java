package com.zero.domain;

public enum FxRateSource implements CodedEnum {
  /** 从汇率接口自动获取 */
  API(1),
  /** 用户在快照中手动填写，不会被自动覆盖 */
  MANUAL(2),
  /** 保存快照时接口不可用，沿用了更早日期的汇率；之后查询会尝试更新为当天真实汇率 */
  FALLBACK(3);

  private final int code;

  FxRateSource(int code) {
    this.code = code;
  }

  @Override
  public int code() {
    return code;
  }
}
