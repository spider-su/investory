package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small, deterministic financial derivations shared by page-result adapters. */
public final class RetirementFinancialCalculations {
  private RetirementFinancialCalculations() {}

  public static BigDecimal positiveDifference(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) return null;
    return left.subtract(right).max(BigDecimal.ZERO);
  }

  public static BigDecimal percentageOf(BigDecimal amount, BigDecimal total) {
    if (amount == null || total == null || total.signum() == 0) return BigDecimal.ZERO;
    return amount
        .max(BigDecimal.ZERO)
        .multiply(BigDecimal.valueOf(100))
        .divide(total, 1, RoundingMode.HALF_UP);
  }

  public static BigDecimal cappedPercentageOf(BigDecimal amount, BigDecimal total) {
    if (amount == null || total == null || total.signum() == 0) return BigDecimal.ZERO;
    return percentageOf(amount.min(total), total);
  }

  public static BigDecimal monthly(BigDecimal annual) {
    return annual == null ? null : annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
  }

  public static BigDecimal difference(BigDecimal expected, BigDecimal current) {
    return expected == null || current == null ? null : expected.subtract(current);
  }
}
