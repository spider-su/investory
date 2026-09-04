package com.smartbox.investory.longterm.application.model;

import com.smartbox.investory.longterm.api.model.RentalEconomicsModel;
import com.smartbox.investory.longterm.application.service.LongTermAssetCalculator;
import com.smartbox.investory.shared.presentation.FinancialPrecision;
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
  public BigDecimal monthlyNetIncomeAfterTax() {
    return netAnnualIncomeAfterTax.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
  }

  public static AnnualEconomics of(
      BigDecimal value, BigDecimal gross, BigDecimal expenses, BigDecimal tax) {
    return calculate(value, gross, expenses, tax, FinancialPrecision.CALCULATION_RATIO_SCALE);
  }

  /** Aggregate yields historically retain 12 decimal places. */
  public static AnnualEconomics aggregateOf(
      BigDecimal value, BigDecimal gross, BigDecimal expenses, BigDecimal tax) {
    return calculate(value, gross, expenses, tax, 12);
  }

  private static AnnualEconomics calculate(
      BigDecimal value, BigDecimal gross, BigDecimal expenses, BigDecimal tax, int yieldScale) {
    RentalEconomicsModel rental = RentalEconomicsModel.of(gross, expenses, tax);
    BigDecimal netBeforeTax = rental.netIncomeBeforeTax();
    BigDecimal netAfterTax = rental.netIncome();
    return new AnnualEconomics(
        gross,
        expenses,
        tax,
        netBeforeTax,
        netAfterTax,
        ratio(gross, value, yieldScale),
        ratio(netBeforeTax, value, yieldScale),
        ratio(netAfterTax, value, yieldScale));
  }

  private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator, int scale) {
    if (scale == FinancialPrecision.CALCULATION_RATIO_SCALE)
      return LongTermAssetCalculator.ratio(numerator, denominator);
    return denominator == null || denominator.signum() == 0
        ? BigDecimal.ZERO
        : numerator.divide(denominator, scale, RoundingMode.HALF_UP);
  }
}
