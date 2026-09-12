package com.smartbox.investory.accounting;

import java.time.LocalDate;

/** Inclusive effective-dated JDG activity period. */
public record BusinessActivityPeriod(LocalDate validFrom, LocalDate validTo) {
  public BusinessActivityPeriod {
    if (validFrom == null) throw new IllegalArgumentException("JDG start date is required");
    if (validTo != null && validTo.isBefore(validFrom))
      throw new IllegalArgumentException("JDG end date cannot precede start date");
  }

  public boolean activeOn(LocalDate date) {
    return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
  }
}
