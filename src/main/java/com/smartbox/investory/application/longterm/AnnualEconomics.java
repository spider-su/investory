package com.smartbox.investory.application.longterm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Normalized annual economics used across all long-term asset types. */
public record AnnualEconomics(
    BigDecimal grossAnnualIncome,
    BigDecimal annualExpenses,
    BigDecimal annualTax,
    BigDecimal netAnnualIncomeBeforeTax,
    BigDecimal netAnnualIncomeAfterTax,
    BigDecimal grossYield,
    BigDecimal netYieldBeforeTax,
    BigDecimal netYieldAfterTax) {
  public static AnnualEconomics of(
      BigDecimal value, BigDecimal gross, BigDecimal expenses, BigDecimal tax) {
    BigDecimal netBeforeTax = gross.subtract(expenses);
    BigDecimal netAfterTax = netBeforeTax.subtract(tax);
    return new AnnualEconomics(
        gross,
        expenses,
        tax,
        netBeforeTax,
        netAfterTax,
        LongTermAssetCalculator.ratio(gross, value),
        LongTermAssetCalculator.ratio(netBeforeTax, value),
        LongTermAssetCalculator.ratio(netAfterTax, value));
  }

  /** Aggregate yields historically retain 12 decimal places. */
  public static AnnualEconomics aggregateOf(
      BigDecimal value, BigDecimal gross, BigDecimal expenses, BigDecimal tax) {
    BigDecimal netBeforeTax = gross.subtract(expenses);
    BigDecimal netAfterTax = netBeforeTax.subtract(tax);
    return new AnnualEconomics(
        gross,
        expenses,
        tax,
        netBeforeTax,
        netAfterTax,
        ratio(gross, value),
        ratio(netBeforeTax, value),
        ratio(netAfterTax, value));
  }

  private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
    return denominator == null || denominator.signum() == 0
        ? BigDecimal.ZERO
        : numerator.divide(denominator, 12, RoundingMode.HALF_UP);
  }
}
