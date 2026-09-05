package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/** Public Long-Term API model. */
public record AnnualEconomicsView(
    BigDecimal grossAnnualIncome,
    BigDecimal annualExpenses,
    BigDecimal annualTax,
    BigDecimal monthlyTaxBase,
    BigDecimal monthlyTax,
    BigDecimal netAnnualIncomeBeforeTax,
    BigDecimal netAnnualIncomeAfterTax,
    BigDecimal monthlyNetIncomeAfterTax,
    BigDecimal grossYield,
    BigDecimal netYieldBeforeTax,
    BigDecimal netYieldAfterTax) {
  public AnnualEconomicsView(
      BigDecimal grossAnnualIncome,
      BigDecimal annualExpenses,
      BigDecimal annualTax,
      BigDecimal netAnnualIncomeBeforeTax,
      BigDecimal netAnnualIncomeAfterTax,
      BigDecimal monthlyNetIncomeAfterTax,
      BigDecimal grossYield,
      BigDecimal netYieldBeforeTax,
      BigDecimal netYieldAfterTax) {
    this(
        grossAnnualIncome,
        annualExpenses,
        annualTax,
        BigDecimal.ZERO,
        annualTax.divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
        netAnnualIncomeBeforeTax,
        netAnnualIncomeAfterTax,
        monthlyNetIncomeAfterTax,
        grossYield,
        netYieldBeforeTax,
        netYieldAfterTax);
  }

  public BigDecimal annualExpensesAndTax() {
    return annualExpenses.add(annualTax);
  }
}
