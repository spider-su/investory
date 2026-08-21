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
    withdrawal = nz(withdrawal).max(BigDecimal.ZERO).min(startValue.add(nz(reviewAdjustment)));
    reviewAdjustment = nz(reviewAdjustment);
    endValue = startValue.add(reviewAdjustment).subtract(withdrawal).max(BigDecimal.ZERO);
    source = source == null ? ProjectionSource.PROJECTED : source;
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
