package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/** Public Long-Term API model. */
public record AnnualEconomicsView(
    BigDecimal grossAnnualIncome,
    BigDecimal annualExpenses,
    BigDecimal annualTax,
    BigDecimal netAnnualIncomeBeforeTax,
    BigDecimal netAnnualIncomeAfterTax,
    BigDecimal monthlyNetIncomeAfterTax,
    BigDecimal grossYield,
    BigDecimal netYieldBeforeTax,
    BigDecimal netYieldAfterTax) {
  public BigDecimal annualExpensesAndTax() {
    return annualExpenses.add(annualTax);
  }
}
