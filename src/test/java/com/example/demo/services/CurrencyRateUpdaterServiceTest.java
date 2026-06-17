package com.example.demo.services;

import com.example.demo.clients.ExchangeRateClient;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.currency.CurrencyRateUpdaterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    void updateCurrencyRates_pushesRatesForUsdEurAndPln() throws Exception {
        when(exchangeRateClient.getLatestRates(anyString(), eq("USD,EUR,PLN"), eq("test-key")))
                .thenAnswer(invocation -> {
                    String base = invocation.getArgument(0);
                    ExchangeRateClient.ExchangeRateResponse response = new ExchangeRateClient.ExchangeRateResponse();
                    Map<String, Double> quotes = new HashMap<>();
                    quotes.put(base + "USD", 1.0);
                    quotes.put(base + "EUR", 0.9);
                    quotes.put(base + "PLN", 4.0);
                    response.setQuotes(quotes);
                    return response;
                });

        // Run the schedule-equivalent on a separate thread so the production-code Thread.sleep does not
        // freeze the test.
        Thread runner = new Thread(() -> updater.updateCurrencyRates());
        runner.start();
        runner.join(10_000);

        ArgumentCaptor<CurrencyType> baseCaptor = ArgumentCaptor.forClass(CurrencyType.class);
        ArgumentCaptor<Map<CurrencyType, Double>> ratesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(currencyRateService, org.mockito.Mockito.times(3))
                .updateRates(baseCaptor.capture(), ratesCaptor.capture());

        // Verify USD invocation contains expected rates.
        Map<CurrencyType, Double> usdRates = null;
        for (int i = 0; i < baseCaptor.getAllValues().size(); i++) {
            if (baseCaptor.getAllValues().get(i) == CurrencyType.USD) {
                usdRates = ratesCaptor.getAllValues().get(i);
            }
        }
        assertEquals(0.9, usdRates.get(CurrencyType.EUR));
        assertEquals(4.0, usdRates.get(CurrencyType.PLN));
    }

    @Test
    void updateCurrencyRates_throwsWhenResponseIsNull() {
        when(exchangeRateClient.getLatestRates(anyString(), anyString(), anyString())).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> updater.updateCurrencyRates());
    }
}

