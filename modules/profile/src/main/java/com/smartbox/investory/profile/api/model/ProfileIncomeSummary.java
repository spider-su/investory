package com.smartbox.investory.profile.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

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
    marketIncomeYtd = zeroIfNull(marketIncomeYtd);
    marketAnnualIncome = zeroIfNull(marketAnnualIncome);
    marketNetYield = zeroIfNull(marketNetYield);
    longTermAnnualIncome = zeroIfNull(longTermAnnualIncome);
    longTermNetYield = zeroIfNull(longTermNetYield);
    combinedAnnualIncome = zeroIfNull(combinedAnnualIncome);
    combinedNetYield = zeroIfNull(combinedNetYield);
  }

  public static BigDecimal ratio(BigDecimal amount, BigDecimal value) {
    return amount == null || value == null || value.signum() == 0
        ? BigDecimal.ZERO
        : amount.divide(value, 8, RoundingMode.HALF_UP);
  }

  /** Planned long-term income accrued through the supplied calendar month. */
  public BigDecimal longTermIncomeToDate(int month) {
    if (month < 1 || month > 12) return null;
    return longTermAnnualIncome
        .multiply(BigDecimal.valueOf(month))
        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
  }
}
