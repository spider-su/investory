package com.smartbox.investory.retirement.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Comparable annual income facts for the two Profile asset sources. These yields measure projected
 * net income only; they are not cash-flow-neutral total investment return or annualized KPI return.
 */
public record ProfileIncomeSummary(
    BigDecimal marketIncomeYtd,
    BigDecimal marketAnnualIncome,
    BigDecimal marketNetYield,
    BigDecimal longTermAnnualIncome,
    BigDecimal longTermNetYield,
    BigDecimal combinedAnnualIncome,
    BigDecimal combinedNetYield) {

  public ProfileIncomeSummary {
    marketIncomeYtd = nz(marketIncomeYtd);
    marketAnnualIncome = nz(marketAnnualIncome);
    marketNetYield = nz(marketNetYield);
    longTermAnnualIncome = nz(longTermAnnualIncome);
    longTermNetYield = nz(longTermNetYield);
    combinedAnnualIncome = nz(combinedAnnualIncome);
    combinedNetYield = nz(combinedNetYield);
  }

  public static ProfileIncomeSummary legacy(
      BigDecimal marketIncome,
      BigDecimal marketValue,
      BigDecimal longTermIncome,
      BigDecimal longTermValue,
      BigDecimal totalIncome,
      BigDecimal totalValue) {
    return new ProfileIncomeSummary(
        marketIncome,
        marketIncome,
        ratio(marketIncome, marketValue),
        longTermIncome,
        ratio(longTermIncome, longTermValue),
        totalIncome,
        ratio(totalIncome, totalValue));
  }

  public String marketIncomeYtdCompactDisplay() {
    return ProfilePresentation.compactMoney(marketIncomeYtd);
  }

  public String marketAnnualIncomeCompactDisplay() {
    return ProfilePresentation.compactMoney(marketAnnualIncome);
  }

  public String marketNetYieldDisplay() {
    return ProfilePresentation.percentage(marketNetYield);
  }

  public String longTermAnnualIncomeCompactDisplay() {
    return ProfilePresentation.compactMoney(longTermAnnualIncome);
  }

  public String longTermNetYieldDisplay() {
    return ProfilePresentation.percentage(longTermNetYield);
  }

  public String combinedAnnualIncomeCompactDisplay() {
    return ProfilePresentation.compactMoney(combinedAnnualIncome);
  }

  public String combinedNetYieldDisplay() {
    return ProfilePresentation.percentage(combinedNetYield);
  }

  public static BigDecimal ratio(BigDecimal amount, BigDecimal value) {
    return amount == null || value == null || value.signum() == 0
        ? BigDecimal.ZERO
        : amount.divide(value, 8, RoundingMode.HALF_UP);
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
