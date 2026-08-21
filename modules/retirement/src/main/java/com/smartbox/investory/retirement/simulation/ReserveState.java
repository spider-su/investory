package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

public record ReserveState(
    BigDecimal startValue,
    BigDecimal withdrawal,
    BigDecimal reviewAdjustment,
    BigDecimal endValue,
    ProjectionSource source) {
  public ReserveState {
    startValue = nz(startValue);
    reviewAdjustment = nz(reviewAdjustment);
    withdrawal = nz(withdrawal).max(BigDecimal.ZERO)
        .min(startValue.add(reviewAdjustment).max(BigDecimal.ZERO));
    endValue = startValue.add(reviewAdjustment).subtract(withdrawal).max(BigDecimal.ZERO);
    source = source == null ? ProjectionSource.PROJECTED : source;
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
