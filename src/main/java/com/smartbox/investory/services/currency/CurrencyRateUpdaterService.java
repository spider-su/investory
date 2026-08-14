package com.smartbox.investory.services.currency;

import com.smartbox.investory.clients.currency.ExchangeRateClient;
import com.smartbox.investory.clients.currency.ExchangeRateException;
import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.integration.PluginConfig;
import com.smartbox.investory.integration.config.IntegrationConfigurationService;
import com.smartbox.investory.integration.fx.ExchangeRateHostFxDataPlugin;
import com.smartbox.investory.integration.fx.FxDataPlugin;
import com.smartbox.investory.integration.fx.FxQuote;
import com.smartbox.investory.integration.fx.FxRequest;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@Transactional
public class CurrencyRateUpdaterService {

  private final FxDataPlugin fxDataPlugin;
  private final ExchangeRateClient legacyExchangeRateClient;
  private final CurrencyRateService currencyRateService;

  @Autowired(required = false)
  private IntegrationConfigurationService integrationConfigurationService;

  @Autowired(required = false)
  private com.smartbox.investory.services.InvestmentCalculationCache calculationCache;

  @Value("${app.api.exchange-rate-key}")
  private String apiKey;

  @org.springframework.beans.factory.annotation.Autowired
  public CurrencyRateUpdaterService(
      FxDataPlugin fxDataPlugin, CurrencyRateService currencyRateService) {
    this.fxDataPlugin = fxDataPlugin;
    this.legacyExchangeRateClient = null;
    this.currencyRateService = currencyRateService;
  }

  /** Compatibility constructor for callers still mocking the legacy transport client. */
  public CurrencyRateUpdaterService(
      ExchangeRateClient exchangeRateClient, CurrencyRateService currencyRateService) {
    this.fxDataPlugin = null;
    this.legacyExchangeRateClient = exchangeRateClient;
    this.currencyRateService = currencyRateService;
  }

  public CurrencyRateRefreshResult updateCurrencyRates() {
    return updateCurrencyRatesForDate(LocalDate.now());
  }

  public CurrencyRateRefreshResult updateCurrencyRatesForDate(LocalDate effectiveDate) {
    PluginConfig fallback = PluginConfig.of("apiKey", apiKey);
    PluginConfig resolved =
        integrationConfigurationService == null
            ? fallback
            : integrationConfigurationService.resolveForRuntime(
                com.smartbox.investory.integration.IntegrationType.FX_DATA,
                ExchangeRateHostFxDataPlugin.ID,
                fallback);
    return refresh(effectiveDate, resolved);
  }

  public CurrencyRateRefreshResult updateCurrencyRatesForDate(
      LocalDate effectiveDate, PluginConfig config) {
    return refresh(effectiveDate, config);
  }

  private CurrencyRateRefreshResult refresh(LocalDate effectiveDate, PluginConfig config) {
    try {
      Map<CurrencyType, Double> usdRates;
      LocalDate providerDate;
      if (fxDataPlugin != null) {
        List<FxQuote> quotes =
            fxDataPlugin.fetchRates(
                new FxRequest(
                    CurrencyType.USD, List.of(CurrencyType.EUR, CurrencyType.PLN), effectiveDate),
                config);
        usdRates = parseUsdRates(quotes);
        providerDate =
            quotes.stream()
                .map(FxQuote::providerDate)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(effectiveDate);
      } else {
        ExchangeRateClient.ExchangeRateResponse response =
            legacyExchangeRateClient.getLatestRates("USD", "EUR,PLN", apiKey);
        usdRates = parseUsdRates(response);
        providerDate = response.getDate() != null ? response.getDate() : effectiveDate;
      }
      Map<CurrencyType, Map<CurrencyType, Double>> ratesByBase = deriveCrossRates(usdRates);
      ratesByBase.forEach(
          (base, rates) -> currencyRateService.updateRates(base, rates, providerDate));
      currencyRateService.activateDailyHistoryAt(providerDate);
      if (calculationCache != null) {
        calculationCache.invalidate();
      }
      return new CurrencyRateRefreshResult(providerDate, List.of("USD", "EUR", "PLN"), List.of());
    } catch (ExchangeRateException e) {
      log.warn("Skipping FX refresh: {}", e.getMessage());
      return new CurrencyRateRefreshResult(
          effectiveDate, List.of(), List.of("USD: " + e.getMessage()));
    } catch (IllegalArgumentException e) {
      log.warn("Skipping FX refresh: {}", e.getMessage());
      return new CurrencyRateRefreshResult(
          effectiveDate, List.of(), List.of("USD: " + e.getMessage()));
    }
  }

  private Map<CurrencyType, Double> parseUsdRates(
      ExchangeRateClient.ExchangeRateResponse response) {
    if (response == null || CollectionUtils.isEmpty(response.getQuotes())) {
      throw new IllegalArgumentException("empty exchangerate.host response");
    }
    Map<CurrencyType, Double> rates = new EnumMap<>(CurrencyType.class);
    rates.put(CurrencyType.USD, 1.0);
    response
        .getQuotes()
        .forEach(
            (key, value) -> rates.put(CurrencyType.valueOf(key.substring("USD".length())), value));
    for (CurrencyType currency : CurrencyType.values()) {
      if (!rates.containsKey(currency)
          || rates.get(currency) == null
          || rates.get(currency) == 0.0) {
        throw new IllegalArgumentException("missing USD -> " + currency + " rate");
      }
    }
    return rates;
  }

  private Map<CurrencyType, Double> parseUsdRates(List<FxQuote> quotes) {
    if (quotes == null || quotes.isEmpty()) {
      throw new IllegalArgumentException("empty FX plugin response");
    }
    Map<CurrencyType, Double> rates = new java.util.EnumMap<>(CurrencyType.class);
    rates.put(CurrencyType.USD, 1.0);
    quotes.forEach(quote -> rates.put(quote.target(), quote.rate()));
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
