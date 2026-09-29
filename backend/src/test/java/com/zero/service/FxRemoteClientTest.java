package com.zero.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zero.domain.Currency;
import com.zero.domain.FxProvider;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FxRemoteClientTest {

  private static final ObjectMapper OM = new ObjectMapper();
  private static final LocalDate DAY = LocalDate.parse("2026-09-20");

  private static final String FRANKFURTER = "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-09-18\",\"rates\":{\"CNY\":6.6976}}";
  private static final String CURRENCY_API = "{\"date\":\"2026-09-20\",\"usd\":{\"aed\":3.6725,\"cny\":6.7016}}";
  private static final String ER_API = "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"CNY\":6.719488,\"HKD\":7.84}}";

  /** 按 URL 前缀返回预置响应，未命中的数据源视为不可用 */
  private static class StubClient extends FxRemoteClient {
    final Map<FxProvider, String> bodies = new HashMap<>();
    final List<FxProvider> called = new ArrayList<>();

    StubClient() {
      super(OM);
    }

    @Override
    protected String get(URI uri) {
      for (FxProvider p : FxProvider.values()) {
        if (uri.getHost().equals(url(p, Currency.USD, DAY).getHost()) || uri.getHost().equals(url(p, Currency.USD, null).getHost())) {
          called.add(p);
          String body = bodies.get(p);
          if (body == null) {
            throw new IllegalStateException("down");
          }
          return body;
        }
      }
      throw new IllegalStateException("unknown host " + uri);
    }
  }

  @Test
  void usesFirstAvailableProvider() {
    StubClient c = new StubClient();
    c.bodies.put(FxProvider.FRANKFURTER, FRANKFURTER);
    c.bodies.put(FxProvider.CURRENCY_API_JSDELIVR, CURRENCY_API);
    FxRemoteClient.Fetched f = c.fetch(Currency.USD, DAY);
    assertEquals(6.6976, f.rate());
    assertEquals(FxProvider.FRANKFURTER, f.provider());
    assertEquals(List.of(FxProvider.FRANKFURTER), c.called);
  }

  @Test
  void fallsThroughProvidersInOrder() {
    StubClient c = new StubClient();
    c.bodies.put(FxProvider.CURRENCY_API_CLOUDFLARE, CURRENCY_API);
    FxRemoteClient.Fetched f = c.fetch(Currency.USD, DAY);
    assertEquals(6.7016, f.rate());
    assertEquals(FxProvider.CURRENCY_API_CLOUDFLARE, f.provider());
    assertEquals(
        List.of(FxProvider.FRANKFURTER, FxProvider.CURRENCY_API_JSDELIVR, FxProvider.CURRENCY_API_CLOUDFLARE),
        c.called);
  }

  @Test
  void latestOnlyProviderIsSkippedForHistoricalDates() {
    StubClient c = new StubClient();
    c.bodies.put(FxProvider.EXCHANGE_RATE_API, ER_API);
    assertThrows(IllegalStateException.class, () -> c.fetch(Currency.USD, DAY));
    assertEquals(false, c.called.contains(FxProvider.EXCHANGE_RATE_API));

    FxRemoteClient.Fetched latest = c.fetch(Currency.USD, null);
    assertEquals(6.719488, latest.rate());
    assertEquals(FxProvider.EXCHANGE_RATE_API, latest.provider());
  }

  @Test
  void currencyApiIsSkippedBeforeItsHistoryStarts() {
    StubClient c = new StubClient();
    c.bodies.put(FxProvider.CURRENCY_API_JSDELIVR, CURRENCY_API);
    assertThrows(IllegalStateException.class, () -> c.fetch(Currency.USD, LocalDate.parse("2023-06-15")));
    assertEquals(List.of(FxProvider.FRANKFURTER), c.called);
  }

  @Test
  void interruptStopsFailover() {
    FxRemoteClient c =
        new FxRemoteClient(OM) {
          @Override
          protected String get(URI uri) throws InterruptedException {
            throw new InterruptedException();
          }
        };
    assertThrows(IllegalStateException.class, () -> c.fetch(Currency.USD, DAY));
    assertEquals(true, Thread.interrupted());
  }

  @Test
  void rejectsMalformedResponses() {
    assertThrows(Exception.class, () -> FxRemoteClient.parse(OM, FxProvider.FRANKFURTER, Currency.USD, "{\"rates\":{}}"));
    assertThrows(
        Exception.class,
        () -> FxRemoteClient.parse(OM, FxProvider.EXCHANGE_RATE_API, Currency.USD, "{\"result\":\"error\",\"rates\":{\"CNY\":7}}"));
    assertThrows(
        Exception.class, () -> FxRemoteClient.parse(OM, FxProvider.CURRENCY_API_JSDELIVR, Currency.USD, "{\"usd\":{\"cny\":0}}"));
  }

  @Test
  void buildsProviderUrls() {
    assertEquals(
        "https://api.frankfurter.dev/v1/2026-09-20?from=HKD&to=CNY",
        FxRemoteClient.url(FxProvider.FRANKFURTER, Currency.HKD, DAY).toString());
    assertEquals(
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/hkd.min.json",
        FxRemoteClient.url(FxProvider.CURRENCY_API_JSDELIVR, Currency.HKD, null).toString());
    assertEquals(
        "https://2026-09-20.currency-api.pages.dev/v1/currencies/usd.min.json",
        FxRemoteClient.url(FxProvider.CURRENCY_API_CLOUDFLARE, Currency.USD, DAY).toString());
  }
}
