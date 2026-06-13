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
        if (!exchangeRateCache.containsKey(baseCurrency) || !exchangeRateCache.get(baseCurrency).containsKey(positionCurrency)) {
            preloadExchangeRates();
        }

        // Return the rate from the cache
        double rate = exchangeRateCache.get(baseCurrency).get(positionCurrency);
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

