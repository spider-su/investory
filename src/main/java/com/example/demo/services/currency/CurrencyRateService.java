package com.example.demo.services.currency;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CurrencyRate;
import com.example.demo.infrastructure.repository.CurrencyRateRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    public static final long MAX_FX_AGE_DAYS = 45;

    private final CurrencyRateRepository currencyRateRepository;

    private static final Map<CurrencyType, Map<CurrencyType, NavigableMap<LocalDate, Double>>> exchangeRateCache =
            new ConcurrentHashMap<>();

    public double convertToBaseCurrency(double amount, CurrencyType baseCurrency, CurrencyType positionCurrency) {
        return convertToBaseCurrency(amount, baseCurrency, positionCurrency, LocalDate.now());
    }

    public double convertToBaseCurrency(double amount, CurrencyType baseCurrency, CurrencyType positionCurrency, LocalDate rateDate) {
        FxRateResolution resolution = resolveRate(positionCurrency, baseCurrency, rateDate);
        if (!resolution.isUsable()) {
            preloadExchangeRates();
            resolution = resolveRate(positionCurrency, baseCurrency, rateDate);
        }
        if (!resolution.isUsable()) {
            throw new FxRateUnavailableException(
                    positionCurrency, baseCurrency, rateDate, resolution.conversionStatus());
        }
        return amount * resolution.fxRateToTarget();
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
        FxRateResolution resolution = resolveRate(base, toCurrency, date);
        if (!resolution.isUsable()) {
            preloadExchangeRates();
            resolution = resolveRate(base, toCurrency, date);
        }
        if (!resolution.isUsable()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(resolution.fxRateToTarget());
    }

    public double getRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        return findRate(base, toCurrency, date)
                .orElseThrow(() -> new RuntimeException(
                        "Rate not found for " + base + " to " + toCurrency + " at " + toMonthStart(date)));
    }

    public FxRateResolution resolveRate(
            CurrencyType sourceCurrency, CurrencyType targetCurrency, LocalDate valuationDate) {
        LocalDate effectiveDate = valuationDate == null ? LocalDate.now() : valuationDate;
        if (sourceCurrency == targetCurrency) {
            return new FxRateResolution(1.0, "SAME_CURRENCY", effectiveDate, 0, "SAME_CURRENCY");
        }

        RateObservation direct = findCachedRate(sourceCurrency, targetCurrency, effectiveDate);
        if (direct != null) {
            return resolution(direct.rate(), "DIRECT", direct.rateDate(), effectiveDate);
        }
        RateObservation inverse = findCachedRate(targetCurrency, sourceCurrency, effectiveDate);
        if (inverse != null && inverse.rate() != 0.0) {
            return resolution(1.0 / inverse.rate(), "INVERSE", inverse.rateDate(), effectiveDate);
        }
        for (CurrencyType pivot : CurrencyType.values()) {
            if (pivot == sourceCurrency || pivot == targetCurrency) {
                continue;
            }
            RateObservation first = resolveStoredEdge(sourceCurrency, pivot, effectiveDate);
            RateObservation second = resolveStoredEdge(pivot, targetCurrency, effectiveDate);
            if (first != null && second != null) {
                LocalDate sourceDate = first.rateDate().isBefore(second.rateDate())
                        ? first.rateDate()
                        : second.rateDate();
                return resolution(
                        first.rate() * second.rate(), "TRIANGULATED:" + pivot, sourceDate, effectiveDate);
            }
        }
        return new FxRateResolution(0.0, "MISSING", null, null, "MISSING");
    }

    private RateObservation resolveStoredEdge(
            CurrencyType sourceCurrency, CurrencyType targetCurrency, LocalDate valuationDate) {
        RateObservation direct = findCachedRate(sourceCurrency, targetCurrency, valuationDate);
        if (direct != null) {
            return direct;
        }
        RateObservation inverse = findCachedRate(targetCurrency, sourceCurrency, valuationDate);
        if (inverse == null || inverse.rate() == 0.0) {
            return null;
        }
        return new RateObservation(1.0 / inverse.rate(), inverse.rateDate());
    }

    private FxRateResolution resolution(
            double rate, String source, LocalDate sourceRateDate, LocalDate valuationDate) {
        int ageDays = Math.toIntExact(ChronoUnit.DAYS.between(sourceRateDate, valuationDate));
        String status = ageDays > MAX_FX_AGE_DAYS ? "STALE" : "OK";
        return new FxRateResolution(rate, source, sourceRateDate, ageDays, status);
    }

    private RateObservation findCachedRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
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
        return floor == null ? null : new RateObservation(floor.getValue(), floor.getKey());
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

    private record RateObservation(double rate, LocalDate rateDate) {}

    public record FxRateResolution(
            double fxRateToTarget,
            String source,
            LocalDate sourceRateDate,
            Integer ageDays,
            String conversionStatus) {
        public boolean isUsable() {
            return "OK".equals(conversionStatus) || "SAME_CURRENCY".equals(conversionStatus);
        }
    }
}

