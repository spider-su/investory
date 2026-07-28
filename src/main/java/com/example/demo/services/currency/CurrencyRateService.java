package com.example.demo.services.currency;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CurrencyRate;
import com.example.demo.infrastructure.repository.CurrencyRateRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CurrencyRateService {

    private final CurrencyRateRepository currencyRateRepository;

    private static final Map<CurrencyType, Map<CurrencyType, NavigableMap<LocalDate, Double>>> exchangeRateCache =
            new ConcurrentHashMap<>();

    public double convertToBaseCurrency(double amount, CurrencyType baseCurrency, CurrencyType positionCurrency) {
        return convertToBaseCurrency(amount, baseCurrency, positionCurrency, LocalDate.now());
    }

    public double convertToBaseCurrency(double amount, CurrencyType baseCurrency, CurrencyType positionCurrency, LocalDate rateDate) {
        // Same currency needs no rate (and must not depend on a stored USD->USD rate).
        if (baseCurrency == positionCurrency) {
            return amount;
        }

        LocalDate rateMonth = toMonthStart(rateDate);
        if (!hasRate(baseCurrency, positionCurrency, rateMonth)) {
            preloadExchangeRates();
        }

        Double rate = getHistoricalRate(baseCurrency, positionCurrency, rateMonth);
        if (rate == null || rate == 0.0) {
            // No FX data loaded yet: don't blow up the
            // whole dashboard/import — fall back to the unconverted amount and warn.
            log.warn("Missing FX rate {} -> {}; returning amount unconverted", baseCurrency, positionCurrency);
            return amount;
        }
        return amount / rate; // Convert the amount to the base currency
    }

    public void preloadExchangeRates() {
        exchangeRateCache.clear();
        List<CurrencyRate> rates = currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscMonthStartAsc();
        for (CurrencyRate rate : rates) {
            cacheRate(rate.getBase(), rate.getToCurrency(), rate.getMonthStart(), rate.getRate());
        }
    }

    public void updateRates(CurrencyType base, Map<CurrencyType, Double> rates, LocalDate date) {
        LocalDate rateMonth = toMonthStart(date);
        rates.forEach((toCurrency, rate) -> {
            CurrencyRate currencyRate = currencyRateRepository.findByMonthStartAndBaseAndToCurrency(rateMonth, base, toCurrency)
                    .orElseGet(() -> {
                        CurrencyRate newRate = new CurrencyRate();
                        newRate.setMonthStart(rateMonth);
                        newRate.setBase(base);
                        newRate.setToCurrency(toCurrency);
                        return newRate;
                    });

            currencyRate.setMonthStart(rateMonth);
            currencyRate.setRate(rate);
            currencyRateRepository.save(currencyRate);
            cacheRate(base, toCurrency, rateMonth, rate);
        });
    }

    public double getRate(CurrencyType base, CurrencyType toCurrency) {
        return getRate(base, toCurrency, LocalDate.now());
    }

    public OptionalDouble findRate(CurrencyType base, CurrencyType toCurrency) {
        return findRate(base, toCurrency, LocalDate.now());
    }

    public OptionalDouble findRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        if (base == toCurrency) {
            return OptionalDouble.of(1.0);
        }
        LocalDate rateMonth = toMonthStart(date);
        if (!hasRate(base, toCurrency, rateMonth)) {
            preloadExchangeRates();
        }
        Double rate = getHistoricalRate(base, toCurrency, rateMonth);
        if (rate == null || rate == 0.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(rate);
    }

    public double getRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        return findRate(base, toCurrency, date)
                .orElseThrow(() -> new RuntimeException(
                        "Rate not found for " + base + " to " + toCurrency + " at " + toMonthStart(date)));
    }

    private boolean hasRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        return getHistoricalRate(base, toCurrency, date) != null;
    }

    private Double getHistoricalRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        Double direct = findCachedRate(base, toCurrency, date);
        if (direct != null) {
            return direct;
        }

        Double inverse = findCachedRate(toCurrency, base, date);
        if (inverse == null || inverse == 0.0) {
            return null;
        }
        return 1.0 / inverse;
    }

    private Double findCachedRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        Map<CurrencyType, NavigableMap<LocalDate, Double>> byBase = exchangeRateCache.get(base);
        if (byBase == null) {
            return null;
        }
        NavigableMap<LocalDate, Double> byPair = byBase.get(toCurrency);
        if (CollectionUtils.isEmpty(byPair)) {
            return null;
        }

        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        Map.Entry<LocalDate, Double> floor = byPair.floorEntry(effectiveDate);
        if (floor != null) {
            return floor.getValue();
        }
        return byPair.firstEntry() != null ? byPair.firstEntry().getValue() : null;
    }

    private void cacheRate(CurrencyType base, CurrencyType toCurrency, LocalDate date, double rate) {
        exchangeRateCache
                .computeIfAbsent(base, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(toCurrency, ignored -> new ConcurrentSkipListMap<>())
                .put(date, rate);
    }

    private LocalDate toMonthStart(LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        return effectiveDate.withDayOfMonth(1);
    }
}

