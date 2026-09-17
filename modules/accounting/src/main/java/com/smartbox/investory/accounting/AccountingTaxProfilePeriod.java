package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Narrow effective-dated tax applicability used by the operational POC. */
public record AccountingTaxProfilePeriod(
    LocalDate validFrom,
    LocalDate validTo,
    boolean jdgActive,
    BigDecimal ryczaltRate,
    boolean vatRegistered,
    boolean vatEuRegistered,
    String zusRegime,
    boolean voluntarySickness) {
  public AccountingTaxProfilePeriod {
    if (validFrom == null) throw new IllegalArgumentException("tax profile start date is required");
    if (validTo != null && validTo.isBefore(validFrom))
      throw new IllegalArgumentException("tax profile end date cannot precede start date");
    if (ryczaltRate == null || ryczaltRate.signum() < 0)
      throw new IllegalArgumentException("ryczalt rate must be non-negative");
  }

  public boolean activeOn(LocalDate date) {
    return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
  }
}
