package com.smartbox.investory.investment.valuation.fx;

import com.smartbox.investory.investment.port.fx.FxRateProvider;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxQuote;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxRequest;
import com.smartbox.investory.investment.port.fx.FxRateProviderException;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class CurrencyRateUpdaterService {

  private final FxRateProvider fxRateProvider;
  private final CurrencyRateService currencyRateService;
  private final com.smartbox.investory.investment.performance.InvestmentCalculationCache
      calculationCache;
  private final PortfolioProjectionRefreshService projectionRefreshService;

  public CurrencyRateUpdaterService(
      FxRateProvider fxRateProvider,
      CurrencyRateService currencyRateService,
      com.smartbox.investory.investment.performance.InvestmentCalculationCache calculationCache,
      PortfolioProjectionRefreshService projectionRefreshService) {
    this.fxRateProvider = fxRateProvider;
    this.currencyRateService = currencyRateService;
    this.calculationCache = calculationCache;
    this.projectionRefreshService = projectionRefreshService;
  }

  public CurrencyRateRefreshResult updateCurrencyRates() {
    return updateCurrencyRatesForDate(LocalDate.now());
  }

  public CurrencyRateRefreshResult updateCurrencyRatesForDate(LocalDate effectiveDate) {
    return refresh(effectiveDate);
  }

  private CurrencyRateRefreshResult refresh(LocalDate effectiveDate) {
    try {
      List<FxQuote> quotes =
          fxRateProvider.fetchRates(
              new FxRequest(
                  CurrencyType.USD, List.of(CurrencyType.EUR, CurrencyType.PLN), effectiveDate));
      validateQuotes(quotes, effectiveDate);
      Map<CurrencyType, Double> usdRates = parseUsdRates(quotes);
      LocalDate providerDate = quotes.getFirst().providerDate();
      Map<CurrencyType, Map<CurrencyType, Double>> ratesByBase = deriveCrossRates(usdRates);
      ratesByBase.forEach(
          (base, rates) -> currencyRateService.updateRates(base, rates, providerDate));
      currencyRateService.activateDailyHistoryAt(providerDate);
      projectionRefreshService.refreshApplicationViews(
          PortfolioProjectionRefreshService.ApplicationRefreshScope.FX_UPDATE);
      calculationCache.invalidate();
      return new CurrencyRateRefreshResult(providerDate, List.of("USD", "EUR", "PLN"), List.of());
    } catch (FxRateProviderException e) {
      log.warn("Skipping FX refresh: {}", e.getMessage());
      return new CurrencyRateRefreshResult(
          effectiveDate, List.of(), List.of("USD: " + e.getMessage()));
    } catch (IllegalArgumentException e) {
      log.warn("Skipping FX refresh: {}", e.getMessage());
      return new CurrencyRateRefreshResult(
          effectiveDate, List.of(), List.of("USD: " + e.getMessage()));
    }
  }

  private Map<CurrencyType, Double> parseUsdRates(List<FxQuote> quotes) {
    if (quotes == null || quotes.isEmpty()) {
      throw new IllegalArgumentException("empty FX plugin response");
    }
    Map<CurrencyType, Double> rates = new java.util.EnumMap<>(CurrencyType.class);
    rates.put(CurrencyType.USD, 1.0);
    quotes.forEach(quote -> rates.put(quote.target(), quote.rate().doubleValue()));
    for (CurrencyType currency : CurrencyType.values()) {
      if (!rates.containsKey(currency)
          || rates.get(currency) == null
          || !Double.isFinite(rates.get(currency))
          || rates.get(currency) <= 0.0) {
        throw new IllegalArgumentException("missing USD -> " + currency + " rate");
      }
    }
    return rates;
  }

  private void validateQuotes(List<FxQuote> quotes, LocalDate effectiveDate) {
    if (quotes == null || quotes.isEmpty()) {
      throw new IllegalArgumentException("empty FX plugin response");
    }
    Map<CurrencyType, FxQuote> byTarget = new EnumMap<>(CurrencyType.class);
    for (FxQuote quote : quotes) {
      if (quote == null
          || quote.base() == null
          || quote.target() == null
          || quote.rate() == null
          || quote.effectiveDate() == null
          || quote.providerDate() == null) {
        throw new IllegalArgumentException("incomplete FX plugin quote");
      }
      if (quote.base() != CurrencyType.USD
          || quote.target() == CurrencyType.USD
          || quote.target() == quote.base()
          || !effectiveDate.equals(quote.effectiveDate())
          || quote.providerDate().isAfter(effectiveDate)) {
        throw new IllegalArgumentException("inconsistent FX plugin quote metadata");
      }
      if (quote.rate().signum() <= 0) {
        throw new IllegalArgumentException("invalid FX plugin rate");
      }
      double numericRate = quote.rate().doubleValue();
      if (!Double.isFinite(numericRate) || numericRate <= 0.0) {
        throw new IllegalArgumentException("invalid FX plugin rate");
      }
      if (byTarget.putIfAbsent(quote.target(), quote) != null) {
        FxQuote previous = byTarget.get(quote.target());
        if (!Objects.equals(previous.rate(), quote.rate())
            || !Objects.equals(previous.providerDate(), quote.providerDate())) {
          throw new IllegalArgumentException("conflicting duplicate FX plugin quote");
        }
      }
    }
    for (CurrencyType target : List.of(CurrencyType.EUR, CurrencyType.PLN)) {
      if (!byTarget.containsKey(target)) {
        throw new IllegalArgumentException("missing USD -> " + target + " rate");
      }
    }
    LocalDate providerDate = quotes.getFirst().providerDate();
    if (quotes.stream().anyMatch(quote -> !providerDate.equals(quote.providerDate()))) {
      throw new IllegalArgumentException("inconsistent FX provider dates");
    }
  }

  private Map<CurrencyType, Map<CurrencyType, Double>> deriveCrossRates(
      Map<CurrencyType, Double> usdRates) {
    Map<CurrencyType, Map<CurrencyType, Double>> ratesByBase = new EnumMap<>(CurrencyType.class);
    for (CurrencyType base : CurrencyType.values()) {
      Map<CurrencyType, Double> rates = new HashMap<>();
      double usdToBase = usdRates.get(base);
      for (CurrencyType target : CurrencyType.values()) {
        if (target != base) {
          rates.put(target, usdRates.get(target) / usdToBase);
        }
      }
      ratesByBase.put(base, rates);
    }
    return ratesByBase;
  }

  public record CurrencyRateRefreshResult(
      LocalDate rateDate, List<String> updated, List<String> failed) {
    public LocalDate month() {
      return rateDate;
    }
  }
}
