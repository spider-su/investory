package com.smartbox.investory.investment.imports.xtb;

import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class XtbPositionCurrencyResolver {

  private static final double RATE_TOLERANCE = 0.005;
  private static final double VALUE_TOLERANCE = 0.05;

  public Resolution resolve(
      PositionEntity position,
      CurrencyType accountCurrency,
      CurrencyType configuredCurrency,
      CurrencyType symbolCurrency) {
    List<Double> brokerRates = brokerRates(position);
    if (brokerRates.isEmpty()) {
      return new Resolution(
          firstNonNull(configuredCurrency, symbolCurrency, accountCurrency), false);
    }

    boolean hasSameCurrencyRate = brokerRates.stream().anyMatch(this::isSameCurrencyRate);
    boolean hasConversionRate = brokerRates.stream().anyMatch(rate -> !isSameCurrencyRate(rate));
    if (hasSameCurrencyRate && hasConversionRate) {
      throw invalid(position, "open and close conversion rates imply different quote currencies");
    }

    Resolution resolution;
    if (hasSameCurrencyRate) {
      resolution = new Resolution(accountCurrency, false);
    } else {
      resolution =
          nonAccountCandidate(position, accountCurrency, configuredCurrency, symbolCurrency);
    }

    validateValue(
        position,
        "open",
        position.getOpenPrice(),
        position.getPurchaseValue(),
        position.getOpenConversionRate());
    validateValue(
        position,
        "close",
        position.getClosePrice(),
        position.getSaleValue(),
        position.getCloseConversionRate());
    return resolution;
  }

  private Resolution nonAccountCandidate(
      PositionEntity position,
      CurrencyType accountCurrency,
      CurrencyType configuredCurrency,
      CurrencyType symbolCurrency) {
    CurrencyType configured = configuredCurrency == accountCurrency ? null : configuredCurrency;
    CurrencyType inferred = symbolCurrency == accountCurrency ? null : symbolCurrency;
    if (configured != null && inferred != null && configured != inferred) {
      throw invalid(
          position, "ambiguous quote currency: configured=" + configured + ", symbol=" + inferred);
    }
    CurrencyType resolved = configured != null ? configured : inferred;
    if (resolved == null) {
      return new Resolution(accountCurrency, true);
    }
    return new Resolution(resolved, false);
  }

  private List<Double> brokerRates(PositionEntity position) {
    List<Double> rates = new ArrayList<>(2);
    addPositive(rates, position.getOpenConversionRate());
    addPositive(rates, position.getCloseConversionRate());
    return rates;
  }

  private void addPositive(List<Double> rates, BigDecimal rate) {
    if (rate != null && rate.signum() > 0) {
      rates.add(rate.doubleValue());
    }
  }

  private boolean isSameCurrencyRate(double rate) {
    return Math.abs(rate - 1.0) <= RATE_TOLERANCE;
  }

  private void validateValue(
      PositionEntity position,
      String leg,
      BigDecimal price,
      BigDecimal value,
      BigDecimal conversionRate) {
    if (price == null
        || value == null
        || conversionRate == null
        || position.getVolume() == null
        || position.getVolume().signum() == 0) {
      return;
    }
    double expected =
        position.getVolume().multiply(price).multiply(conversionRate).abs().doubleValue();
    double actual = value.abs().doubleValue();
    double tolerance = Math.max(0.05, actual * VALUE_TOLERANCE);
    if (Math.abs(expected - actual) > tolerance) {
      throw invalid(
          position,
          leg
              + " value does not match volume * price * conversion rate: expected="
              + expected
              + ", actual="
              + actual);
    }
  }

  private IllegalArgumentException invalid(PositionEntity position, String reason) {
    return new IllegalArgumentException(
        "Invalid XTB position currency for account "
            + position.getAccount()
            + ", symbol "
            + position.getSymbol()
            + ", position "
            + position.getSourcePositionId()
            + ": "
            + reason);
  }

  private CurrencyType firstNonNull(CurrencyType... currencies) {
    for (CurrencyType currency : currencies) {
      if (currency != null) {
        return currency;
      }
    }
    return null;
  }

  public record Resolution(CurrencyType priceCurrency, boolean normalizePricesToAccountCurrency) {}
}
