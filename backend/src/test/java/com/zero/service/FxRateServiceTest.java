package com.zero.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zero.domain.Currency;
import com.zero.domain.FxProvider;
import com.zero.domain.FxRate;
import com.zero.domain.FxRateSource;
import com.zero.mapper.FxRateMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FxRateServiceTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-09-28");
  private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");

  private FxRateMapper mapper;
  private Double remoteRate;
  private int remoteCalls;
  private FxRateService service;

  @BeforeEach
  void setUp() {
    mapper = mock(FxRateMapper.class);
    remoteRate = 7.1;
    remoteCalls = 0;
    service =
        new FxRateService(mapper, null) {
          @Override
          protected FxRemoteClient.Fetched fetchRemote(Currency currency, LocalDate date) {
            remoteCalls++;
            if (remoteRate == null) {
              throw new IllegalStateException("offline");
            }
            return new FxRemoteClient.Fetched(remoteRate, FxProvider.CURRENCY_API_JSDELIVR);
          }

          @Override
          protected LocalDate today() {
            return TODAY;
          }

          @Override
          protected Instant now() {
            return NOW;
          }
        };
  }

  private static FxRate stored(String date, double rate, FxRateSource source, Instant fetchedAt) {
    FxRate r = new FxRate();
    r.setCurrency(Currency.USD);
    r.setRateDate(date);
    r.setRate(rate);
    r.setSource(source);
    r.setFetchedAt(fetchedAt.toString());
    return r;
  }

  @Test
  void pastDateUsesStoredRateWithoutNetwork() {
    when(mapper.find(Currency.USD, "2026-09-01")).thenReturn(stored("2026-09-01", 7.0, FxRateSource.API, NOW.minusSeconds(86400 * 30)));
    assertEquals(7.0, service.quote(Currency.USD, "2026-09-01").rate());
    assertEquals(0, remoteCalls);
  }

  @Test
  void missingRateIsFetchedAndSaved() {
    FxRateService.Quote q = service.quote(Currency.USD, "2026-09-01");
    assertEquals(7.1, q.rate());
    assertEquals(1, remoteCalls);
    assertEquals(FxProvider.CURRENCY_API_JSDELIVR, q.provider());
    verify(mapper)
        .upsert(
            argThat(
                r -> r.getRate() == 7.1
                    && r.getSource() == FxRateSource.API
                    && r.getProvider() == FxProvider.CURRENCY_API_JSDELIVR
                    && r.getRateDate().equals("2026-09-01")));
  }

  @Test
  void staleTodayRateIsRefreshed() {
    when(mapper.find(Currency.USD, "2026-09-28")).thenReturn(stored("2026-09-28", 7.0, FxRateSource.API, NOW.minusSeconds(7200)));
    assertEquals(7.1, service.quote(Currency.USD, "2026-09-28").rate());
    assertEquals(1, remoteCalls);
  }

  @Test
  void manualTodayRateIsNeverOverwritten() {
    when(mapper.find(Currency.USD, "2026-09-28")).thenReturn(stored("2026-09-28", 6.9, FxRateSource.MANUAL, NOW.minusSeconds(7200)));
    assertEquals(6.9, service.quote(Currency.USD, "2026-09-28").rate());
    assertEquals(0, remoteCalls);
  }

  @Test
  void offlineFallsBackToEarlierRate() {
    remoteRate = null;
    when(mapper.findLatestOnOrBefore(Currency.USD, "2026-09-28")).thenReturn(stored("2026-09-20", 7.05, FxRateSource.API, NOW));
    FxRateService.Quote q = service.quote(Currency.USD, "2026-09-28");
    assertEquals(7.05, q.rate());
    assertTrue(q.fallback());
    assertEquals("2026-09-20", q.rateDate());
  }

  @Test
  void offlineWithoutAnyRateReturnsNull() {
    remoteRate = null;
    assertNull(service.quote(Currency.USD, "2026-09-28").rate());
  }

  @Test
  void ensureStoresProvidedRateAsManual() {
    service.ensureRatesForSnapshot("2026-09-01", Set.of(Currency.USD), Map.of(Currency.USD, 6.8));
    verify(mapper).upsert(argThat(r -> r.getRate() == 6.8 && r.getSource() == FxRateSource.MANUAL));
    assertEquals(0, remoteCalls);
  }

  @Test
  void ensureSkipsWriteWhenProvidedRateUnchanged() {
    when(mapper.find(Currency.USD, "2026-09-01")).thenReturn(stored("2026-09-01", 7.0, FxRateSource.API, NOW));
    service.ensureRatesForSnapshot("2026-09-01", Set.of(Currency.USD), Map.of(Currency.USD, 7.0));
    verify(mapper, never()).upsert(any());
  }

  @Test
  void ensureRejectsInvalidOrUnavailableRate() {
    assertThrows(
        ResponseStatusException.class,
        () -> service.ensureRatesForSnapshot("2026-09-01", Set.of(Currency.USD), Map.of(Currency.USD, 0.0)));
    remoteRate = null;
    assertThrows(
        ResponseStatusException.class,
        () -> service.ensureRatesForSnapshot("2026-09-01", Set.of(Currency.HKD), Map.of()));
  }

  @Test
  void ensurePinsFallbackRateToSnapshotDate() {
    remoteRate = null;
    when(mapper.findLatestOnOrBefore(Currency.USD, "2026-09-01")).thenReturn(stored("2026-08-20", 7.05, FxRateSource.API, NOW));
    service.ensureRatesForSnapshot("2026-09-01", Set.of(Currency.USD), Map.of());
    verify(mapper).upsert(argThat(r -> r.getRate() == 7.05 && r.getSource() == FxRateSource.FALLBACK && r.getRateDate().equals("2026-09-01")));
  }

  @Test
  void fallbackRateIsReplacedOnceApiRecovers() {
    when(mapper.find(Currency.USD, "2026-09-01")).thenReturn(stored("2026-09-01", 7.05, FxRateSource.FALLBACK, NOW));
    FxRateService.Quote q = service.quote(Currency.USD, "2026-09-01");
    assertEquals(7.1, q.rate());
    assertEquals(FxRateSource.API, q.source());
    assertEquals(1, remoteCalls);
  }

  @Test
  void fallbackRateStaysFlaggedWhileOffline() {
    remoteRate = null;
    when(mapper.find(Currency.USD, "2026-09-01")).thenReturn(stored("2026-09-01", 7.05, FxRateSource.FALLBACK, NOW));
    FxRateService.Quote q = service.quote(Currency.USD, "2026-09-01");
    assertEquals(7.05, q.rate());
    assertTrue(q.fallback());
  }

  @Test
  void baseCurrencyNeedsNoRate() {
    service.ensureRatesForSnapshot("2026-09-01", Set.of(Currency.CNY), null);
    assertEquals(0, remoteCalls);
    verify(mapper, never()).upsert(any());
  }
}
