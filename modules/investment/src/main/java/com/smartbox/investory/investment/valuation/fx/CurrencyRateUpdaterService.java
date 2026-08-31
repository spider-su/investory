package com.smartbox.investory.investment.valuation.fx;

import com.smartbox.investory.investment.port.fx.FxRateProvider;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxQuote;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxRequest;
import com.smartbox.investory.investment.port.fx.FxRateProviderException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class CurrencyRateUpdaterService {

  private final FxRateProvider fxRateProvider;
  private final CurrencyRateService currencyRateService;

  @Autowired(required = false)
  private com.smartbox.investory.investment.performance.InvestmentCalculationCache calculationCache;

  public CurrencyRateUpdaterService(
      FxRateProvider fxRateProvider, CurrencyRateService currencyRateService) {
    this.fxRateProvider = fxRateProvider;
    this.currencyRateService = currencyRateService;
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
      Map<CurrencyType, Double> usdRates = parseUsdRates(quotes);
      LocalDate providerDate =
          quotes.stream()
              .map(FxQuote::providerDate)
              .filter(java.util.Objects::nonNull)
              .findFirst()
              .orElse(effectiveDate);
      Map<CurrencyType, Map<CurrencyType, Double>> ratesByBase = deriveCrossRates(usdRates);
      ratesByBase.forEach(
          (base, rates) -> currencyRateService.updateRates(base, rates, providerDate));
      currencyRateService.activateDailyHistoryAt(providerDate);
      if (calculationCache != null) {
        calculationCache.invalidate();
      }
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
          || rates.get(currency) == 0.0) {
        throw new IllegalArgumentException("missing USD -> " + currency + " rate");
      }
    }
    return rates;
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
