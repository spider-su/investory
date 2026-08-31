package com.smartbox.investory.retirement.simulation;

public enum SensitivityDriver {
  INFLATION("Inflation"),
  RECURRING_SPENDING("Spending"),
  SPENDING_GROWTH("Cost growth"),
  EQUITY_RETURN("Equity return"),
  FIXED_INCOME_RETURN("Fixed-income return"),
  RENTAL_INCOME_GROWTH("Rental-income growth"),
  PENSION("Pension");

  private final String label;

  SensitivityDriver(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public SensitivityDriverCategory category() {
    return switch (this) {
      case RECURRING_SPENDING, PENSION -> SensitivityDriverCategory.PLANNING_LEVER;
      default -> SensitivityDriverCategory.ECONOMIC_DRIVER;
    };
  }
}
