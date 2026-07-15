package com.example.demo.services;

import com.example.demo.clients.currency.ExchangeRateClient;
import com.example.demo.clients.currency.ExchangeRateException;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.currency.CurrencyRateUpdaterService;
import com.example.demo.services.currency.CurrencyRateUpdaterService.CurrencyRateRefreshResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyRateUpdaterServiceTest {

    @Mock
    private ExchangeRateClient exchangeRateClient;
    @Mock
    private CurrencyRateService currencyRateService;

    private CurrencyRateUpdaterService updater;

    @BeforeEach
    void setUp() {
        updater = new CurrencyRateUpdaterService(exchangeRateClient, currencyRateService);
        ReflectionTestUtils.setField(updater, "apiKey", "test-key");
    }

    @Test
    void updateCurrencyRates_pushesRatesForUsdEurAndPln() {
        when(exchangeRateClient.getLatestRates("USD", "USD,EUR,PLN", "test-key"))
                .thenReturn(response(Map.of("USDUSD", 1.0, "USDEUR", 0.9, "USDPLN", 4.0)));

        CurrencyRateRefreshResult result = updater.updateCurrencyRates();

        ArgumentCaptor<CurrencyType> baseCaptor = ArgumentCaptor.forClass(CurrencyType.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<CurrencyType, Double>> ratesCaptor =
                (ArgumentCaptor<Map<CurrencyType, Double>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
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
        assertEquals(1.0 / 0.9, java.util.Objects.requireNonNull(eurRates).get(CurrencyType.USD), 0.000001);
        assertEquals(4.0 / 0.9, eurRates.get(CurrencyType.PLN), 0.000001);
        assertEquals(1, monthCaptor.getAllValues().getFirst().getDayOfMonth());
        assertEquals(List.of("USD", "EUR", "PLN"), result.updated());
        assertTrue(result.failed().isEmpty());
        verify(exchangeRateClient).getLatestRates("USD", "USD,EUR,PLN", "test-key");
    }

    @Test
    void updateCurrencyRatesForMonth_writesOnlyThatMonthStart() {
        when(exchangeRateClient.getLatestRates("USD", "USD,EUR,PLN", "test-key"))
                .thenReturn(response(Map.of("USDUSD", 1.0, "USDEUR", 0.9, "USDPLN", 4.0)));

        CurrencyRateRefreshResult result = updater.updateCurrencyRatesForMonth(LocalDate.of(2026, 8, 17));

        ArgumentCaptor<LocalDate> monthCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(currencyRateService, org.mockito.Mockito.times(3))
                .updateRates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), monthCaptor.capture());
        assertEquals(List.of(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 1)), monthCaptor.getAllValues());
        assertEquals(LocalDate.of(2026, 8, 1), result.month());
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
        when(exchangeRateClient.getLatestRates("USD", "USD,EUR,PLN", "test-key"))
                .thenThrow(new ExchangeRateException("exchangerate.host returned HTTP 429 for /live"));

        CurrencyRateRefreshResult result = updater.updateCurrencyRates();

        assertTrue(result.updated().isEmpty());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().getFirst().contains("429"));
    }

    private static ExchangeRateClient.ExchangeRateResponse response(Map<String, Double> quotes) {
        ExchangeRateClient.ExchangeRateResponse response = new ExchangeRateClient.ExchangeRateResponse();
        response.setQuotes(quotes);
        return response;
    }
}

