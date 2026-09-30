package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/** Public Long-Term API model. */
public record AnnualEconomicsView(
    BigDecimal grossAnnualIncome,
    BigDecimal annualExpenses,
    BigDecimal annualTax,
    BigDecimal annualRentalTaxBase,
    BigDecimal annualRentalIncomeTax,
    BigDecimal monthlyTaxBase,
    BigDecimal monthlyRentalIncomeTax,
    BigDecimal monthlyTax,
    BigDecimal annualPropertyTax,
    BigDecimal annualInsurance,
    BigDecimal annualPropertyTaxAndInsurance,
    BigDecimal monthlyPropertyTaxAndInsurance,
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
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        annualTax.divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
        annualTax.divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
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
