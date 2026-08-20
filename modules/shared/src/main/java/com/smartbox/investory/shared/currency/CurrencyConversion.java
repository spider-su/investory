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
}
