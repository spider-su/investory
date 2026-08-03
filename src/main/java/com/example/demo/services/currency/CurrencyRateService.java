package com.example.demo.services.currency;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.CurrencyRate;
import com.example.demo.infrastructure.repository.CurrencyRateRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
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
    private static final int FX_SCALE = 8;
    private static final MathContext FX_MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

    private final CurrencyRateRepository currencyRateRepository;

    private static final Map<CurrencyType, Map<CurrencyType, NavigableMap<LocalDate, BigDecimal>>> exchangeRateCache =
            new ConcurrentHashMap<>();

    public double convertToBaseCurrency(double amount, CurrencyType baseCurrency, CurrencyType positionCurrency) {
        return convertToBaseCurrency(
                BigDecimal.valueOf(amount), baseCurrency, positionCurrency, LocalDate.now())
                .doubleValue();
    }

    public double convertToBaseCurrency(double amount, CurrencyType baseCurrency, CurrencyType positionCurrency, LocalDate rateDate) {
        return convertToBaseCurrency(
                BigDecimal.valueOf(amount), baseCurrency, positionCurrency, rateDate)
                .doubleValue();
    }

    public BigDecimal convertToBaseCurrency(
            BigDecimal amount, CurrencyType baseCurrency, CurrencyType positionCurrency) {
        return convertToBaseCurrency(amount, baseCurrency, positionCurrency, LocalDate.now());
    }

    public BigDecimal convertToBaseCurrency(
            BigDecimal amount, CurrencyType baseCurrency, CurrencyType positionCurrency, LocalDate rateDate) {
        FxRateResolution resolution = resolveRate(positionCurrency, baseCurrency, rateDate);
        if (!resolution.isUsable()) {
            preloadExchangeRates();
            resolution = resolveRate(positionCurrency, baseCurrency, rateDate);
        }
        if (!resolution.isUsable()) {
            throw new FxRateUnavailableException(
                    positionCurrency, baseCurrency, rateDate, resolution.conversionStatus());
        }
        if (amount == null) {
            return BigDecimal.ZERO.setScale(FX_SCALE, RoundingMode.HALF_UP);
        }
        return amount.multiply(resolution.fxRateToTarget(), FX_MATH_CONTEXT)
                .setScale(FX_SCALE, RoundingMode.HALF_UP);
    }

    public void preloadExchangeRates() {
        exchangeRateCache.clear();
        List<CurrencyRate> rates = currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscMonthStartAsc();
        for (CurrencyRate rate : rates) {
            cacheRate(rate.getBase(), rate.getToCurrency(), rate.getMonthStart(), rate.getRateValue());
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
            cacheRate(base, toCurrency, rateMonth, BigDecimal.valueOf(rate));
        });
    }

    public double getRate(CurrencyType base, CurrencyType toCurrency) {
        return getRate(base, toCurrency, LocalDate.now());
    }

    public OptionalDouble findRate(CurrencyType base, CurrencyType toCurrency) {
        return findRate(base, toCurrency, LocalDate.now());
    }

    public OptionalDouble findRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        Optional<BigDecimal> rate = findRateDecimal(base, toCurrency, date);
        return rate.isPresent() ? OptionalDouble.of(rate.get().doubleValue()) : OptionalDouble.empty();
    }

    public Optional<BigDecimal> findRateDecimal(CurrencyType base, CurrencyType toCurrency) {
        return findRateDecimal(base, toCurrency, LocalDate.now());
    }

    public Optional<BigDecimal> findRateDecimal(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        FxRateResolution resolution = resolveRate(base, toCurrency, date);
        if (!resolution.isUsable()) {
            preloadExchangeRates();
            resolution = resolveRate(base, toCurrency, date);
        }
        if (!resolution.isUsable()) {
            return Optional.empty();
        }
        return Optional.of(resolution.fxRateToTarget());
    }

    public double getRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        return getRateDecimal(base, toCurrency, date).doubleValue();
    }

    public BigDecimal getRateDecimal(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        return findRateDecimal(base, toCurrency, date)
                .orElseThrow(() -> new RuntimeException(
                        "Rate not found for " + base + " to " + toCurrency + " at " + toMonthStart(date)));
    }

    public FxRateResolution resolveRate(
            CurrencyType sourceCurrency, CurrencyType targetCurrency, LocalDate valuationDate) {
        LocalDate effectiveDate = valuationDate == null ? LocalDate.now() : valuationDate;
        if (sourceCurrency == targetCurrency) {
            return new FxRateResolution(
                    BigDecimal.ONE.setScale(FX_SCALE, RoundingMode.HALF_UP),
                    "SAME_CURRENCY",
                    effectiveDate,
                    0,
                    "SAME_CURRENCY");
        }

        RateObservation direct = findCachedRate(sourceCurrency, targetCurrency, effectiveDate);
        if (direct != null) {
            return resolution(direct.rate(), "DIRECT", direct.rateDate(), effectiveDate);
        }
        RateObservation inverse = findCachedRate(targetCurrency, sourceCurrency, effectiveDate);
        if (inverse != null && inverse.rate().compareTo(BigDecimal.ZERO) != 0) {
            return resolution(
                    BigDecimal.ONE.divide(inverse.rate(), FX_SCALE, RoundingMode.HALF_UP),
                    "INVERSE",
                    inverse.rateDate(),
                    effectiveDate);
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
                        first.rate().multiply(second.rate(), FX_MATH_CONTEXT)
                                .setScale(FX_SCALE, RoundingMode.HALF_UP),
                        "TRIANGULATED:" + pivot,
                        sourceDate,
                        effectiveDate);
            }
        }
        return new FxRateResolution(
                BigDecimal.ZERO.setScale(FX_SCALE, RoundingMode.HALF_UP),
                "MISSING",
                null,
                null,
                "MISSING");
    }

    private RateObservation resolveStoredEdge(
            CurrencyType sourceCurrency, CurrencyType targetCurrency, LocalDate valuationDate) {
        RateObservation direct = findCachedRate(sourceCurrency, targetCurrency, valuationDate);
        if (direct != null) {
            return direct;
        }
        RateObservation inverse = findCachedRate(targetCurrency, sourceCurrency, valuationDate);
        if (inverse == null || inverse.rate().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return new RateObservation(
                BigDecimal.ONE.divide(inverse.rate(), FX_SCALE, RoundingMode.HALF_UP),
                inverse.rateDate());
    }

    private FxRateResolution resolution(
            BigDecimal rate, String source, LocalDate sourceRateDate, LocalDate valuationDate) {
        int ageDays = Math.toIntExact(ChronoUnit.DAYS.between(sourceRateDate, valuationDate));
        String status = ageDays > MAX_FX_AGE_DAYS ? "STALE" : "OK";
        return new FxRateResolution(
                rate.setScale(FX_SCALE, RoundingMode.HALF_UP), source, sourceRateDate, ageDays, status);
    }

    private RateObservation findCachedRate(CurrencyType base, CurrencyType toCurrency, LocalDate date) {
        Map<CurrencyType, NavigableMap<LocalDate, BigDecimal>> byBase = exchangeRateCache.get(base);
        if (byBase == null) {
            return null;
        }
        NavigableMap<LocalDate, BigDecimal> byPair = byBase.get(toCurrency);
        if (CollectionUtils.isEmpty(byPair)) {
            return null;
        }

        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        Map.Entry<LocalDate, BigDecimal> floor = byPair.floorEntry(effectiveDate);
        return floor == null ? null : new RateObservation(floor.getValue(), floor.getKey());
    }

    private void cacheRate(CurrencyType base, CurrencyType toCurrency, LocalDate date, BigDecimal rate) {
        exchangeRateCache
                .computeIfAbsent(base, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(toCurrency, ignored -> new ConcurrentSkipListMap<>())
                .put(date, rate.setScale(FX_SCALE, RoundingMode.HALF_UP));
    }

    private LocalDate toMonthStart(LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        return effectiveDate.withDayOfMonth(1);
    }

    private record RateObservation(BigDecimal rate, LocalDate rateDate) {}

    public record FxRateResolution(
            BigDecimal fxRateToTarget,
            String source,
            LocalDate sourceRateDate,
            Integer ageDays,
            String conversionStatus) {
        public boolean isUsable() {
            return "OK".equals(conversionStatus) || "SAME_CURRENCY".equals(conversionStatus);
        }
    }
}

