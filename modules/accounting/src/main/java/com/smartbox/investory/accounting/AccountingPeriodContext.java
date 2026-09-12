package com.smartbox.investory.accounting;

import java.time.LocalDate;

/** Resolved effective state and accumulated facts supplied to the pure calculator. */
public record AccountingPeriodContext(
    LocalDate period,
    boolean jdgActive,
    boolean qualifyingUop,
    String zusRegime,
    boolean voluntarySickness,
    AccountingYearToDateContext yearToDate,
    ZusCalculationInput zusCalculationInput) {
  public AccountingPeriodContext(
      LocalDate period,
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      AccountingYearToDateContext yearToDate) {
    this(period, jdgActive, qualifyingUop, zusRegime, voluntarySickness, yearToDate, null);
  }

  public AccountingPeriodContext {
    if (period == null) throw new IllegalArgumentException("accounting period is required");
    yearToDate = yearToDate == null ? AccountingYearToDateContext.empty() : yearToDate;
  }

  public static AccountingPeriodContext compatibility(LocalDate period, AccountingProfile profile) {
    return new AccountingPeriodContext(
        period, true, profile.hasUop(), "JDG", false, AccountingYearToDateContext.empty(), null);
  }
}
