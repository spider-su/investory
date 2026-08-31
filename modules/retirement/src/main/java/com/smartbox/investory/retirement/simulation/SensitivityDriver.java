package com.smartbox.investory.retirement.simulation;

public enum SensitivityDriver {
  RECURRING_SPENDING("Spending"),
  SPENDING_GROWTH("Cost growth"),
  EQUITY_RETURN("Equity return"),
  FIXED_INCOME_RETURN("Fixed-income return"),
  RENTAL_INCOME_GROWTH("Rental-income growth"),
  SAFE_RESERVE_YEARS("Safe-reserve target"),
  PENSION("Pension"),
  REAL_ESTATE_RETURN("Real-estate return");

  private final String label;

  SensitivityDriver(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public SensitivityDriverCategory category() {
    return switch (this) {
      case RECURRING_SPENDING, SAFE_RESERVE_YEARS -> SensitivityDriverCategory.POLICY_LEVER;
      default -> SensitivityDriverCategory.RISK;
    };
  }
}
