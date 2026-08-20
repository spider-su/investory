package com.smartbox.investory.longterm.api;

import java.math.BigDecimal;

/** Canonical annual rental economics before any projection or currency conversion. */
public record RentalEconomics(
    BigDecimal grossIncome,
    BigDecimal expenses,
    BigDecimal tax,
    BigDecimal netIncomeBeforeTax,
    BigDecimal netIncome) {
  public static RentalEconomics of(BigDecimal grossIncome, BigDecimal expenses, BigDecimal tax) {
    BigDecimal netBeforeTax = grossIncome.subtract(expenses);
    return new RentalEconomics(
        grossIncome, expenses, tax, netBeforeTax, netBeforeTax.subtract(tax));
  }
}
