package com.example.demo.services;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.CurrencyRate;
import com.example.demo.data.repository.CurrencyRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CurrencyRateService {

    private final CurrencyRateRepository currencyRateRepository;

    private static final Map<CurrencyType, Map<CurrencyType, Double>> exchangeRateCache = new HashMap<>();

    public double convertToBaseCurrency(double amount, CurrencyType baseCurrency, CurrencyType positionCurrency) {
        // Same currency needs no rate (and must not depend on a stored USD->USD rate).
        if (baseCurrency == positionCurrency) {
            return amount;
        }

        if (!exchangeRateCache.containsKey(baseCurrency) || !exchangeRateCache.get(baseCurrency).containsKey(positionCurrency)) {
            preloadExchangeRates();
        }

        Map<CurrencyType, Double> rates = exchangeRateCache.get(baseCurrency);
        Double rate = rates == null ? null : rates.get(positionCurrency);
        if (rate == null || rate == 0.0) {
            // No FX data loaded yet (e.g. /currency/refresh never ran): don't blow up the
            // whole dashboard/import — fall back to the unconverted amount and warn.
            log.warn("Missing FX rate {} -> {}; returning amount unconverted", baseCurrency, positionCurrency);
            return amount;
        }
        return amount / rate; // Convert the amount to the base currency
    }

    public void preloadExchangeRates() {
        // Example of preloading a few common rates into the cache
        List<CurrencyRate> rates = currencyRateRepository.findAll(); // Fetch all rates
        for (CurrencyRate rate : rates) {
            exchangeRateCache
                    .computeIfAbsent(rate.getBase(), k -> new HashMap<>())
                    .put(rate.getToCurrency(), rate.getRate());
        }
    }

    public void updateRates(CurrencyType base, Map<CurrencyType, Double> rates) {
        exchangeRateCache.remove(base);
        rates.forEach((toCurrency, rate) -> {
            CurrencyRate currencyRate = currencyRateRepository.findByBaseAndToCurrency(base, toCurrency)
                    .orElseGet(() -> {
                        CurrencyRate newRate = new CurrencyRate();
                        newRate.setBase(base);
                        newRate.setToCurrency(toCurrency);
                        return newRate;
                    });

            currencyRate.setRate(rate);
            currencyRateRepository.save(currencyRate);
        });
    }

    public double getRate(CurrencyType base, CurrencyType toCurrency) {
        return currencyRateRepository.findByBaseAndToCurrency(base, toCurrency)
                .map(CurrencyRate::getRate)
                .orElseThrow(() -> new RuntimeException("Rate not found for " + base + " to " + toCurrency));
    }
}

