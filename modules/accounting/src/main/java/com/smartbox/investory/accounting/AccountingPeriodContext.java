package com.smartbox.investory.accounting;

import java.time.LocalDate;

/** Resolved effective state and accumulated facts supplied to the pure calculator. */
public record AccountingPeriodContext(
    LocalDate period,
    boolean jdgActive,
    boolean qualifyingUop,
    String zusRegime,
    boolean voluntarySickness,
    java.math.BigDecimal ryczaltRate,
    boolean vatRegistered,
    boolean vatEuRegistered,
    AccountingYearToDateContext yearToDate,
    ZusCalculationInput zusCalculationInput) {
  public AccountingPeriodContext(
      LocalDate period,
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      AccountingYearToDateContext yearToDate) {
    this(
        period,
        jdgActive,
        qualifyingUop,
        zusRegime,
        voluntarySickness,
        null,
        false,
        false,
        yearToDate,
        null);
  }

  public AccountingPeriodContext {
    if (period == null) throw new IllegalArgumentException("accounting period is required");
    yearToDate = yearToDate == null ? AccountingYearToDateContext.empty() : yearToDate;
  }

  public static AccountingPeriodContext compatibility(LocalDate period, AccountingProfile profile) {
    return new AccountingPeriodContext(
        period,
        true,
        profile.hasUop(),
        "JDG",
        false,
        new java.math.BigDecimal("0.12"),
        true,
        true,
        AccountingYearToDateContext.empty(),
        null);
  }
}
