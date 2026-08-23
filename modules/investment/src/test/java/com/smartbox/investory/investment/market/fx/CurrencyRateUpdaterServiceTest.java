package com.smartbox.investory.investment.market.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.fx.client.ExchangeRateClient;
import com.smartbox.investory.investment.infrastructure.fx.client.ExchangeRateException;
import com.smartbox.investory.investment.market.fx.CurrencyRateUpdaterService.CurrencyRateRefreshResult;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CurrencyRateUpdaterServiceTest {

  @Mock private ExchangeRateClient exchangeRateClient;
  @Mock private CurrencyRateService currencyRateService;

  private CurrencyRateUpdaterService updater;

  @BeforeEach
  void setUp() {
    updater = new CurrencyRateUpdaterService(exchangeRateClient, currencyRateService);
    ReflectionTestUtils.setField(updater, "apiKey", "test-key");
  }

  @Test
  void updateCurrencyRates_pushesRatesForUsdEurAndPln() {
    when(exchangeRateClient.getLatestRates("USD", "EUR,PLN", "test-key"))
        .thenReturn(response(Map.of("USDEUR", 0.9, "USDPLN", 4.0)));

    CurrencyRateRefreshResult result = updater.updateCurrencyRates();

    ArgumentCaptor<CurrencyType> baseCaptor = ArgumentCaptor.forClass(CurrencyType.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<CurrencyType, Double>> ratesCaptor =
        (ArgumentCaptor<Map<CurrencyType, Double>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<LocalDate> monthCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(currencyRateService, org.mockito.Mockito.times(3))
        .updateRates(baseCaptor.capture(), ratesCaptor.capture(), monthCaptor.capture());

    // Verify USD invocation contains expected rates.
    Map<CurrencyType, Double> usdRates = null;
    for (int i = 0; i < baseCaptor.getAllValues().size(); i++) {
      if (baseCaptor.getAllValues().get(i) == CurrencyType.USD) {
        usdRates = ratesCaptor.getAllValues().get(i);
      }
    }
    assertEquals(0.9, java.util.Objects.requireNonNull(usdRates).get(CurrencyType.EUR));
    assertEquals(4.0, usdRates.get(CurrencyType.PLN));
    Map<CurrencyType, Double> eurRates = null;
    for (int i = 0; i < baseCaptor.getAllValues().size(); i++) {
      if (baseCaptor.getAllValues().get(i) == CurrencyType.EUR) {
        eurRates = ratesCaptor.getAllValues().get(i);
      }
    }
    assertEquals(
        1.0 / 0.9, java.util.Objects.requireNonNull(eurRates).get(CurrencyType.USD), 0.000001);
    assertEquals(4.0 / 0.9, eurRates.get(CurrencyType.PLN), 0.000001);
    assertEquals(LocalDate.now(), monthCaptor.getAllValues().getFirst());
    assertEquals(List.of("USD", "EUR", "PLN"), result.updated());
    assertTrue(result.failed().isEmpty());
    verify(currencyRateService).activateDailyHistoryAt(LocalDate.now());
    verify(exchangeRateClient).getLatestRates("USD", "EUR,PLN", "test-key");
  }

  @Test
  void updateCurrencyRatesForDate_writesOnlyThatDate() {
    when(exchangeRateClient.getLatestRates("USD", "EUR,PLN", "test-key"))
        .thenReturn(response(Map.of("USDEUR", 0.9, "USDPLN", 4.0)));

    CurrencyRateRefreshResult result =
        updater.updateCurrencyRatesForDate(LocalDate.of(2026, 8, 17));

    ArgumentCaptor<LocalDate> monthCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(currencyRateService, org.mockito.Mockito.times(3))
        .updateRates(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            monthCaptor.capture());
    assertEquals(
        List.of(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17)),
        monthCaptor.getAllValues());
    assertEquals(LocalDate.of(2026, 8, 17), result.rateDate());
    verify(currencyRateService).activateDailyHistoryAt(LocalDate.of(2026, 8, 17));
  }

  @Test
  void updateCurrencyRates_recordsFailureWhenResponseIsNull() {
    when(exchangeRateClient.getLatestRates(anyString(), anyString(), anyString())).thenReturn(null);

    CurrencyRateRefreshResult result = updater.updateCurrencyRates();

    assertTrue(result.updated().isEmpty());
    assertEquals(1, result.failed().size());
  }

  @Test
  void updateCurrencyRates_recordsFailureWhenUsdRequestIsRateLimited() {
    when(exchangeRateClient.getLatestRates("USD", "EUR,PLN", "test-key"))
        .thenThrow(new ExchangeRateException("exchangerate.host returned HTTP 429 for /live"));

    CurrencyRateRefreshResult result = updater.updateCurrencyRates();

    assertTrue(result.updated().isEmpty());
    assertEquals(1, result.failed().size());
    assertTrue(result.failed().getFirst().contains("429"));
  }

  private static ExchangeRateClient.ExchangeRateResponse response(Map<String, Double> quotes) {
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setQuotes(quotes);
    return response;
  }
}
