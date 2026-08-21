package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/** Canonical annual rental economics before any projection or currency conversion. */
public record RentalEconomicsModel(
    BigDecimal grossIncome,
    BigDecimal expenses,
    BigDecimal tax,
    BigDecimal netIncomeBeforeTax,
    BigDecimal netIncome) {
  public static RentalEconomicsModel of(BigDecimal grossIncome, BigDecimal expenses, BigDecimal tax) {
    BigDecimal netBeforeTax = grossIncome.subtract(expenses);
    return new RentalEconomicsModel(
        grossIncome, expenses, tax, netBeforeTax, netBeforeTax.subtract(tax));
  }
}
