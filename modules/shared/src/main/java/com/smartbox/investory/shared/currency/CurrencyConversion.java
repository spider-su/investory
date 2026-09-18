package com.smartbox.investory.shared.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Converts an amount from a source currency into a target currency for a valuation date. */
public interface CurrencyConversion {
  BigDecimal convertToBaseCurrency(
      BigDecimal amount,
      CurrencyType targetCurrency,
      CurrencyType sourceCurrency,
      LocalDate rateDate);

  /** Preloads existing valuation-rate evidence for a bounded calendar range when supported. */
  default void warmValuationMatrices(LocalDate startDate, LocalDate endDate) {
    // Conversion implementations without a local valuation cache do not need preloading.
  }
}
