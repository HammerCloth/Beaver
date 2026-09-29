package com.zero.domain;

public class FxRate {
  private Currency currency;
  private String rateDate;
  private double rate;
  private FxRateSource source;
  /** 自动获取时的数据源，手动填写为 null */
  private FxProvider provider;
  private String fetchedAt;

  public Currency getCurrency() {
    return currency;
  }

  public void setCurrency(Currency currency) {
    this.currency = currency;
  }

  public String getRateDate() {
    return rateDate;
  }

  public void setRateDate(String rateDate) {
    this.rateDate = rateDate;
  }

  public double getRate() {
    return rate;
  }

  public void setRate(double rate) {
    this.rate = rate;
  }

  public FxRateSource getSource() {
    return source;
  }

  public void setSource(FxRateSource source) {
    this.source = source;
  }

  public String getFetchedAt() {
    return fetchedAt;
  }

  public void setFetchedAt(String fetchedAt) {
    this.fetchedAt = fetchedAt;
  }

  public FxProvider getProvider() {
    return provider;
  }

  public void setProvider(FxProvider provider) {
    this.provider = provider;
  }
}
