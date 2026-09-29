package com.zero.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zero.domain.Currency;
import com.zero.domain.FxProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 按 FxProvider 声明顺序逐级获取汇率，任一数据源成功即返回 */
@Component
public class FxRemoteClient {

  private static final Logger log = LoggerFactory.getLogger(FxRemoteClient.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(4);

  public record Fetched(double rate, FxProvider provider) {}

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  public FxRemoteClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * @param date 为 null 表示最新汇率；历史日期只使用覆盖该日期的数据源
   * @return 1 单位 currency 折合的本币
   */
  public Fetched fetch(Currency currency, LocalDate date) {
    for (FxProvider p : FxProvider.values()) {
      if (!p.supports(date)) {
        continue;
      }
      try {
        double rate = parse(objectMapper, p, currency, get(url(p, currency, date)));
        return new Fetched(rate, p);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("获取汇率被中断", e);
      } catch (Exception e) {
        log.warn("汇率源 {} 获取失败 currency={} date={}: {}", p, currency, date, e.toString());
      }
    }
    throw new IllegalStateException("所有汇率源均不可用");
  }

  protected String get(URI uri) throws Exception {
    HttpRequest req = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() != 200) {
      throw new IllegalStateException("HTTP " + resp.statusCode());
    }
    return resp.body();
  }

  static URI url(FxProvider p, Currency currency, LocalDate date) {
    String day = date == null ? "latest" : date.toString();
    String code = currency.name().toLowerCase(Locale.ROOT);
    return URI.create(
        switch (p) {
          case FRANKFURTER ->
              "https://api.frankfurter.dev/v1/" + day + "?from=" + currency + "&to=" + Currency.BASE;
          case CURRENCY_API_JSDELIVR ->
              "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@" + day + "/v1/currencies/" + code + ".min.json";
          case CURRENCY_API_CLOUDFLARE ->
              "https://" + day + ".currency-api.pages.dev/v1/currencies/" + code + ".min.json";
          case EXCHANGE_RATE_API -> "https://open.er-api.com/v6/latest/" + currency;
        });
  }

  static double parse(ObjectMapper om, FxProvider p, Currency currency, String body) throws Exception {
    JsonNode root = om.readTree(body);
    JsonNode rate =
        switch (p) {
          case FRANKFURTER -> root.path("rates").path(Currency.BASE.name());
          case CURRENCY_API_JSDELIVR, CURRENCY_API_CLOUDFLARE ->
              root.path(currency.name().toLowerCase(Locale.ROOT))
                  .path(Currency.BASE.name().toLowerCase(Locale.ROOT));
          case EXCHANGE_RATE_API -> {
            if (!"success".equals(root.path("result").asText())) {
              throw new IllegalStateException("result=" + root.path("result").asText());
            }
            yield root.path("rates").path(Currency.BASE.name());
          }
        };
    if (!rate.isNumber() || !(rate.asDouble() > 0)) {
      throw new IllegalStateException("响应中缺少汇率");
    }
    return rate.asDouble();
  }
}
