package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;

/** Narrow current-year spending fact exposed to Profile; no simulation result leakage. */
public record CurrentYearSpendingProjection(int year, BigDecimal annualSpending, Long planId) {
  public CurrentYearSpendingProjection {
    if (annualSpending == null || annualSpending.signum() < 0) {
      throw new IllegalArgumentException("Annual spending must be non-negative");
    }
  }
}
