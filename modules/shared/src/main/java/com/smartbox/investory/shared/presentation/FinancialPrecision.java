package com.smartbox.investory.shared.presentation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Central precision policy for calculations, reporting values, and presentation boundaries. */
public final class FinancialPrecision {

  public static final int MONEY_SCALE = 2;
  public static final int PERCENTAGE_SCALE = 2;
  public static final int RETURN_SCALE = 4;
  public static final int RATE_SCALE = 6;
  public static final int CALCULATION_RATIO_SCALE = 8;
  public static final RoundingMode REPORTING_ROUNDING = RoundingMode.HALF_UP;

  private FinancialPrecision() {}

  /**
   * Final monetary reporting value. Do not use for imported/source values or aggregation inputs.
   */
  public static BigDecimal money(BigDecimal value) {
    return scale(value, MONEY_SCALE);
  }

  /** Percentage expressed in percentage points, for example 7.125 -> 7.13. */
  public static BigDecimal percentage(BigDecimal value) {
    return scale(value, PERCENTAGE_SCALE);
  }

  /** Percentage ratio converted to percentage points, for example 0.07125 -> 7.13. */
  public static BigDecimal percentageRatio(BigDecimal ratio) {
    return ratio == null ? null : percentage(ratio.multiply(BigDecimal.valueOf(100)));
  }

  /** Display/reporting rate, retaining more precision than an ordinary percentage. */
  public static BigDecimal rate(BigDecimal value) {
    return scale(value, RATE_SCALE);
  }

  /** Return percentage points where small differences matter. */
  public static BigDecimal returnPercentage(BigDecimal value) {
    return scale(value, RETURN_SCALE);
  }

  /** Late-stage ratio used by derived calculations before final display formatting. */
  public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return numerator.divide(denominator, CALCULATION_RATIO_SCALE, REPORTING_ROUNDING);
  }

  private static BigDecimal scale(BigDecimal value, int scale) {
    return value == null ? null : value.setScale(scale, REPORTING_ROUNDING);
  }
}
