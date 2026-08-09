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
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.demo.infrastructure.repository.CashOperation;
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

  private static final Pattern XTB_EXECUTION = Pattern.compile(
      "(?i)currency conversion,\\s*([A-Z]{3})\\s+to\\s+([A-Z]{3}).*?exchange rate:\\s*([0-9]+(?:[\\.,][0-9]+)?)");

  public static final long MAX_FX_AGE_DAYS = 4;
  private static final int FX_SCALE = 8;
  private static final MathContext FX_MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

  private final CurrencyRateRepository currencyRateRepository;

  private static final Map<CurrencyType, Map<CurrencyType, NavigableMap<LocalDate, CachedRate>>>
      exchangeRateCache = new ConcurrentHashMap<>();

  public double convertToBaseCurrency(
      double amount, CurrencyType baseCurrency, CurrencyType positionCurrency) {
    return convertToBaseCurrency(
            BigDecimal.valueOf(amount), baseCurrency, positionCurrency, LocalDate.now())
        .doubleValue();
  }

  public double convertToBaseCurrency(
      double amount, CurrencyType baseCurrency, CurrencyType positionCurrency, LocalDate rateDate) {
    return convertToBaseCurrency(
            BigDecimal.valueOf(amount), baseCurrency, positionCurrency, rateDate)
        .doubleValue();
  }

  public BigDecimal convertToBaseCurrency(
      BigDecimal amount, CurrencyType baseCurrency, CurrencyType positionCurrency) {
    return convertToBaseCurrency(amount, baseCurrency, positionCurrency, LocalDate.now());
  }

  public BigDecimal convertToBaseCurrency(
      BigDecimal amount,
      CurrencyType baseCurrency,
      CurrencyType positionCurrency,
      LocalDate rateDate) {
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
    return amount
        .multiply(resolution.fxRateToTarget(), FX_MATH_CONTEXT)
        .setScale(FX_SCALE, RoundingMode.HALF_UP);
  }

  public void preloadExchangeRates() {
    exchangeRateCache.clear();
    List<CurrencyRate> rates =
        currencyRateRepository.findAllByOrderByBaseAscToCurrencyAscRateDateAsc();
    for (CurrencyRate rate : rates) {
      cacheRate(
          rate.getBase(),
          rate.getToCurrency(),
          rate.getRateDate(),
          rate.getRateValue(),
          rate.getMethod() == null ? "HISTORICAL_MONTHLY" : rate.getMethod(),
          rate.getSource());
    }
  }

  public void updateRates(CurrencyType base, Map<CurrencyType, Double> rates, LocalDate date) {
    rates.forEach(
        (toCurrency, rate) -> {
          CurrencyRate currencyRate =
              currencyRateRepository
                  .findFirstByRateDateAndBaseAndToCurrencyAndSourceAndMethod(
                      date, base, toCurrency, "EXCHANGERATE_HOST", "MARKET_DAILY")
                  .orElseGet(
                      () -> {
                        CurrencyRate newRate = new CurrencyRate();
                        newRate.setRateDate(date);
                        newRate.setBase(base);
                        newRate.setToCurrency(toCurrency);
                        newRate.setSource("EXCHANGERATE_HOST");
                        newRate.setMethod("MARKET_DAILY");
                        return newRate;
                      });

          currencyRate.setRateDate(date);
          currencyRate.setSource("EXCHANGERATE_HOST");
          currencyRate.setMethod("MARKET_DAILY");
          currencyRate.setRate(rate);
          currencyRateRepository.save(currencyRate);
          cacheRate(base, toCurrency, date, BigDecimal.valueOf(rate), "MARKET_DAILY", "EXCHANGERATE_HOST");
        });
  }

  public void harvestXtbExecutionRates(List<CashOperation> operations) {
    if (operations == null) return;
    operations.forEach(operation -> {
      if (operation.getComment() == null || operation.getDate() == null) return;
      Matcher matcher = XTB_EXECUTION.matcher(operation.getComment());
      if (!matcher.find()) return;
      try {
        saveObservation(
            operation.getDate(),
            CurrencyType.valueOf(matcher.group(1).toUpperCase()),
            CurrencyType.valueOf(matcher.group(2).toUpperCase()),
            new BigDecimal(matcher.group(3).replace(',', '.')),
            "XTB",
            "XTB_EXECUTION",
            "XTB:" + matcher.group(1).toUpperCase() + ":" + matcher.group(2).toUpperCase()
                + ":" + operation.getDate().toLocalDate() + ":" + matcher.group(3).replace(',', '.'));
      } catch (IllegalArgumentException ignored) {
        log.debug("Ignoring unsupported XTB FX pair in operation {}", operation.getId());
      }
    });
  }

  public void harvestIbkrExecutionRate(
      ZonedDateTime observedAt, String pair, BigDecimal rate, String sourceReference) {
    if (observedAt == null || pair == null || rate == null || rate.signum() <= 0) return;
    String[] currencies = pair.trim().toUpperCase().split("[./_ -]");
    if (currencies.length != 2) return;
    try {
      saveObservation(
          observedAt,
          CurrencyType.valueOf(currencies[0]),
          CurrencyType.valueOf(currencies[1]),
          rate,
          "IBKR",
          "IBKR_EXECUTION",
          sourceReference);
    } catch (IllegalArgumentException ignored) {
      log.debug("Ignoring unsupported IBKR FX pair {}", pair);
    }
  }

  public void harvestIbkrDailyReference(
      ZonedDateTime observedAt,
      CurrencyType base,
      CurrencyType target,
      BigDecimal rate,
      String sourceReference) {
    if (observedAt == null || base == null || target == null || rate == null || rate.signum() <= 0) return;
    saveObservation(observedAt, base, target, rate, "IBKR", "IBKR_DAILY_REFERENCE", sourceReference);
  }

  private void saveObservation(
      ZonedDateTime observedAt,
      CurrencyType base,
      CurrencyType target,
      BigDecimal rate,
      String source,
      String method,
      String sourceReference) {
    if (base == target || rate.signum() <= 0) return;
    String reference = sourceReference == null ? source + ":" + observedAt + ":" + base + ":" + target : sourceReference;
    CurrencyRate observation = currencyRateRepository.findBySourceReference(reference).orElseGet(CurrencyRate::new);
    observation.setRateDate(observedAt.toLocalDate());
    observation.setBase(base);
    observation.setToCurrency(target);
    observation.setRate(rate);
    observation.setSource(source);
    observation.setMethod(method);
    observation.setObservedAt(observedAt);
    observation.setSourceReference(reference);
    currencyRateRepository.save(observation);
  }

  public FxRateResolution resolveTransactionRate(
      ZonedDateTime transactionTime, CurrencyType sourceCurrency, CurrencyType targetCurrency) {
    if (sourceCurrency == targetCurrency) {
      return new FxRateResolution(BigDecimal.ONE.setScale(FX_SCALE), "SAME_CURRENCY", transactionTime.toLocalDate(), 0, "SAME_CURRENCY", "SAME_CURRENCY", "SAME_CURRENCY");
    }
    List<CurrencyRate> executionRates = currencyRateRepository
        .findAllByMethodInAndObservedAtIsNotNullOrderByObservedAtDesc(List.of("XTB_EXECUTION", "IBKR_EXECUTION"));
    CurrencyRate selected = executionRates.stream()
        .filter(rate -> rate.getObservedAt().compareTo(transactionTime) <= 0)
        .filter(rate -> (rate.getBase() == sourceCurrency && rate.getToCurrency() == targetCurrency)
            || (rate.getBase() == targetCurrency && rate.getToCurrency() == sourceCurrency))
        .findFirst().orElse(null);
    if (selected == null) return new FxRateResolution(BigDecimal.ZERO.setScale(FX_SCALE), "MISSING", null, null, "MISSING_RATE", null, null);
    BigDecimal value = selected.getBase() == sourceCurrency
        ? selected.getRateValue()
        : BigDecimal.ONE.divide(selected.getRateValue(), FX_SCALE, RoundingMode.HALF_UP);
    return new FxRateResolution(value, selected.getMethod(), selected.getRateDate(), 0, "OK", selected.getMethod(), selected.getSource());
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

  public Optional<BigDecimal> findRateDecimal(
      CurrencyType base, CurrencyType toCurrency, LocalDate date) {
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
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Rate not found for "
                        + base
                        + " to "
                        + toCurrency
                        + " at "
                        + date));
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
          "SAME_CURRENCY", "SAME_CURRENCY", "SAME_CURRENCY");
    }

    RateObservation direct = findCachedRate(sourceCurrency, targetCurrency, effectiveDate);
    if (direct != null) {
      return resolution(direct.rate(), "DIRECT", direct.rateDate(), effectiveDate, direct.method(), direct.source());
    }
    RateObservation inverse = findCachedRate(targetCurrency, sourceCurrency, effectiveDate);
    if (inverse != null && inverse.rate().compareTo(BigDecimal.ZERO) != 0) {
      return resolution(
          BigDecimal.ONE.divide(inverse.rate(), FX_SCALE, RoundingMode.HALF_UP),
          "INVERSE",
          inverse.rateDate(),
          effectiveDate, inverse.method(), inverse.source());
    }
    for (CurrencyType pivot : CurrencyType.values()) {
      if (pivot == sourceCurrency || pivot == targetCurrency) {
        continue;
      }
      RateObservation first = resolveStoredEdge(sourceCurrency, pivot, effectiveDate);
      RateObservation second = resolveStoredEdge(pivot, targetCurrency, effectiveDate);
      if (first != null && second != null) {
        LocalDate sourceDate =
            first.rateDate().isBefore(second.rateDate()) ? first.rateDate() : second.rateDate();
        return resolution(
            first
                .rate()
                .multiply(second.rate(), FX_MATH_CONTEXT)
                .setScale(FX_SCALE, RoundingMode.HALF_UP),
            "TRIANGULATED:" + pivot,
            sourceDate,
            effectiveDate, "TRIANGULATED", first.source());
      }
    }
    return new FxRateResolution(
        BigDecimal.ZERO.setScale(FX_SCALE, RoundingMode.HALF_UP), "MISSING", null, null, "MISSING_RATE", null, null);
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
        BigDecimal.ONE.divide(inverse.rate(), FX_SCALE, RoundingMode.HALF_UP), inverse.rateDate(), inverse.method(), inverse.source());
  }

  private FxRateResolution resolution(
      BigDecimal rate, String source, LocalDate sourceRateDate, LocalDate valuationDate, String method, String rateSource) {
    int ageDays = Math.toIntExact(ChronoUnit.DAYS.between(sourceRateDate, valuationDate));
    boolean carryForward = sourceRateDate.isBefore(valuationDate)
        && ("MARKET_DAILY".equals(method) || "IBKR_DAILY_REFERENCE".equals(method))
        && (valuationDate.getDayOfWeek().getValue() >= 6);
    String effectiveMethod = carryForward ? "CARRY_FORWARD" : method;
    String status = ageDays > MAX_FX_AGE_DAYS
        ? ("HISTORICAL_MONTHLY".equals(method) ? "ESTIMATED" : "STALE") : "OK";
    return new FxRateResolution(
        rate.setScale(FX_SCALE, RoundingMode.HALF_UP), source, sourceRateDate, ageDays, status, effectiveMethod, rateSource);
  }

  private RateObservation findCachedRate(
      CurrencyType base, CurrencyType toCurrency, LocalDate date) {
    Map<CurrencyType, NavigableMap<LocalDate, CachedRate>> byBase = exchangeRateCache.get(base);
    if (byBase == null) {
      return null;
    }
    NavigableMap<LocalDate, CachedRate> byPair = byBase.get(toCurrency);
    if (CollectionUtils.isEmpty(byPair)) {
      return null;
    }

    LocalDate effectiveDate = date == null ? LocalDate.now() : date;
    Map.Entry<LocalDate, CachedRate> floor = byPair.floorEntry(effectiveDate);
    return floor == null ? null : new RateObservation(floor.getValue().rate(), floor.getKey(), floor.getValue().method(), floor.getValue().source());
  }

  private void cacheRate(
      CurrencyType base, CurrencyType toCurrency, LocalDate date, BigDecimal rate, String method, String source) {
    if ("XTB_EXECUTION".equals(method) || "IBKR_EXECUTION".equals(method)) return;
    exchangeRateCache
        .computeIfAbsent(base, ignored -> new ConcurrentHashMap<>())
        .computeIfAbsent(toCurrency, ignored -> new ConcurrentSkipListMap<>())
        .put(date, new CachedRate(rate.setScale(FX_SCALE, RoundingMode.HALF_UP), method, source));
  }

  private record CachedRate(BigDecimal rate, String method, String source) {}
  private record RateObservation(BigDecimal rate, LocalDate rateDate, String method, String source) {}

  public record FxRateResolution(
      BigDecimal fxRateToTarget,
      String source,
      LocalDate sourceRateDate,
      Integer ageDays,
      String conversionStatus,
      String rateMethod,
      String rateSource) {
    public boolean isUsable() {
      return "OK".equals(conversionStatus)
          || "ESTIMATED".equals(conversionStatus)
          || "SAME_CURRENCY".equals(conversionStatus);
    }
  }
}
