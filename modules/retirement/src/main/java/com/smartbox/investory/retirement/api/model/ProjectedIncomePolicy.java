package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;

/** Retirement-owned overrides for projected Long-Term cash income. */
public record ProjectedIncomePolicy(
    IncomeMode rentalIncomeMode,
    BigDecimal manualRentalIncome,
    IncomeMode bondCashIncomeMode,
    BigDecimal manualBondCashIncome) {
  public enum IncomeMode {
    SOURCE,
    MANUAL
  }

  public static final ProjectedIncomePolicy SOURCE =
      new ProjectedIncomePolicy(IncomeMode.SOURCE, null, IncomeMode.SOURCE, null);

  public ProjectedIncomePolicy {
    rentalIncomeMode = rentalIncomeMode == null ? IncomeMode.SOURCE : rentalIncomeMode;
    bondCashIncomeMode = bondCashIncomeMode == null ? IncomeMode.SOURCE : bondCashIncomeMode;
    manualRentalIncome = nonNegative(manualRentalIncome);
    manualBondCashIncome = nonNegative(manualBondCashIncome);
  }

  public static ProjectedIncomePolicy source() {
    return SOURCE;
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    if (value == null) return null;
    if (value.signum() < 0)
      throw new IllegalArgumentException("Manual projected income cannot be negative");
    return value;
  }
}
