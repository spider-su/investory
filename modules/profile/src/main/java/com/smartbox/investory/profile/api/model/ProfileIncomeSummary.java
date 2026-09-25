package com.smartbox.investory.profile.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Comparable annual income facts for the two Profile asset sources. Investment result fields are
 * explicitly named because expected total return is not distributable cash income.
 */
public record ProfileIncomeSummary(
    BigDecimal marketIncomeYtd,
    BigDecimal marketAnnualIncome,
    BigDecimal marketNetYield,
    BigDecimal longTermAnnualIncome,
    BigDecimal longTermNetYield,
    BigDecimal combinedAnnualIncome,
    BigDecimal combinedNetYield,
    BigDecimal investmentIncomeBase,
    BigDecimal expectedAnnualInvestmentResult,
    BigDecimal expectedAnnualReturn,
    BigDecimal investmentResultYtd,
    BigDecimal investmentExpectedIncomeYtd,
    BigDecimal investmentExpectationProgress,
    boolean investmentIncomeAvailable,
    BigDecimal marketNetIncomeYtd,
    BigDecimal marketNetAnnualIncome,
    BigDecimal combinedNetAnnualIncome) {

  public ProfileIncomeSummary(
      BigDecimal marketIncomeYtd,
      BigDecimal marketAnnualIncome,
      BigDecimal marketNetYield,
      BigDecimal longTermAnnualIncome,
      BigDecimal longTermNetYield,
      BigDecimal combinedAnnualIncome,
      BigDecimal combinedNetYield) {
    this(
        marketIncomeYtd,
        marketAnnualIncome,
        marketNetYield,
        longTermAnnualIncome,
        longTermNetYield,
        combinedAnnualIncome,
        combinedNetYield,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null);
  }

  public ProfileIncomeSummary {
    marketIncomeYtd = zeroIfNull(marketIncomeYtd);
    marketAnnualIncome = zeroIfNull(marketAnnualIncome);
    marketNetYield = zeroIfNull(marketNetYield);
    longTermAnnualIncome = zeroIfNull(longTermAnnualIncome);
    longTermNetYield = zeroIfNull(longTermNetYield);
    combinedAnnualIncome = zeroIfNull(combinedAnnualIncome);
    combinedNetYield = zeroIfNull(combinedNetYield);
    marketNetIncomeYtd = zeroIfNull(marketNetIncomeYtd);
    marketNetAnnualIncome = zeroIfNull(marketNetAnnualIncome);
    combinedNetAnnualIncome = zeroIfNull(combinedNetAnnualIncome);
  }

  public static BigDecimal ratio(BigDecimal amount, BigDecimal value) {
    return amount == null || value == null || value.signum() == 0
        ? BigDecimal.ZERO
        : amount.divide(value, 8, RoundingMode.HALF_UP);
  }

  /** Planned long-term income accrued through the supplied calendar month. */
  public BigDecimal plannedLongTermIncomeToDate(int month) {
    if (month < 1 || month > 12) return null;
    return longTermAnnualIncome
        .multiply(BigDecimal.valueOf(month))
        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
  }
}
