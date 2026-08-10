package com.example.demo.services.currency;

import com.example.demo.clients.currency.ExchangeRateClient;
import com.example.demo.clients.currency.ExchangeRateException;
import com.example.demo.infrastructure.CurrencyType;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CurrencyRateUpdaterService {

  private final ExchangeRateClient exchangeRateClient;
  private final CurrencyRateService currencyRateService;

  @Value("${app.api.exchange-rate-key}")
  private String apiKey;

  public CurrencyRateRefreshResult updateCurrencyRates() {
    return updateCurrencyRatesForDate(LocalDate.now());
  }

  public CurrencyRateRefreshResult updateCurrencyRatesForDate(LocalDate effectiveDate) {
    return refresh(effectiveDate);
  }

  private CurrencyRateRefreshResult refresh(LocalDate effectiveDate) {
    try {
      ExchangeRateClient.ExchangeRateResponse response =
          exchangeRateClient.getLatestRates("USD", "USD,EUR,PLN", apiKey);
      Map<CurrencyType, Double> usdRates = parseUsdRates(response);
      LocalDate providerDate = response.getDate() != null ? response.getDate() : effectiveDate;
      Map<CurrencyType, Map<CurrencyType, Double>> ratesByBase = deriveCrossRates(usdRates);
      ratesByBase.forEach(
          (base, rates) -> currencyRateService.updateRates(base, rates, providerDate));
      return new CurrencyRateRefreshResult(
          providerDate, List.of("USD", "EUR", "PLN"), List.of());
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

  private Map<CurrencyType, Double> parseUsdRates(ExchangeRateClient.ExchangeRateResponse response) {
    if (response == null || CollectionUtils.isEmpty(response.getQuotes())) {
      throw new IllegalArgumentException("empty exchangerate.host response");
    }
    Map<CurrencyType, Double> rates = new EnumMap<>(CurrencyType.class);
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
    public LocalDate month() { return rateDate; }
  }
}
