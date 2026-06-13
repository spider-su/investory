package com.example.demo.services;

import com.example.demo.clients.ExchangeRateClient;
import com.example.demo.data.CurrencyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CurrencyRateUpdaterService {

    private final ExchangeRateClient exchangeRateClient;
    private final CurrencyRateService currencyRateService;
    @Value("${app.api.exchange-rate-key}")
    private String apiKey;

    public void updateCurrencyRates() {
        try {
            updateRatesForBase("USD");
            Thread.sleep(2000);
            updateRatesForBase("EUR");
            Thread.sleep(2000);
            updateRatesForBase("PLN");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateRatesForBase(String base) {
        ExchangeRateClient.ExchangeRateResponse response = exchangeRateClient.getLatestRates(base, "USD,EUR,PLN", apiKey);
        if (response == null || response.getQuotes() == null) {
            throw new IllegalArgumentException("Failed to fetch exchange rates");
        }
        Map<CurrencyType, Double> rates = new HashMap<>();
        response.getQuotes()
                .forEach((key, value) -> rates.put(CurrencyType.valueOf(key.substring(base.length())), value));
        currencyRateService.updateRates(CurrencyType.valueOf(base), rates);
    }


}

