package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.RentalEconomics;
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
    RentalEconomics rental = RentalEconomics.of(gross, expenses, tax);
    BigDecimal netBeforeTax = rental.netIncomeBeforeTax();
    BigDecimal netAfterTax = rental.netIncome();
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
    RentalEconomics rental = RentalEconomics.of(gross, expenses, tax);
    BigDecimal netBeforeTax = rental.netIncomeBeforeTax();
    BigDecimal netAfterTax = rental.netIncome();
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
