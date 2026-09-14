package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.util.Set;

/** Supported domestic VAT rates; cross-border and exempt treatments have no rate. */
public record AccountingVatRate(BigDecimal value) {
  private static final Set<BigDecimal> SUPPORTED =
      Set.of(BigDecimal.ZERO, new BigDecimal("5"), new BigDecimal("8"), new BigDecimal("23"));

  public AccountingVatRate {
    if (value == null || SUPPORTED.stream().noneMatch(rate -> rate.compareTo(value) == 0)) {
      throw new IllegalArgumentException("VAT rate must be one of 0, 5, 8 or 23");
    }
  }

  public static AccountingVatRate of(BigDecimal value) {
    return new AccountingVatRate(value);
  }

  public static boolean isSupported(BigDecimal value) {
    return value != null && SUPPORTED.stream().anyMatch(rate -> rate.compareTo(value) == 0);
  }
}
