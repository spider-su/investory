package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

/** Immutable aggregate planning state. Instrument mechanics do not cross this boundary. */
public record PlanningBucket(BucketType type, BigDecimal startValue, BigDecimal plannedYieldRate,
    int spendingPriority, BigDecimal targetValue, RefillPolicy refillPolicy) {
  public PlanningBucket {
    type = type == null ? BucketType.CASH : type;
    startValue = nz(startValue);
    plannedYieldRate = nz(plannedYieldRate);
    targetValue = nz(targetValue);
    refillPolicy = refillPolicy == null ? RefillPolicy.NONE : refillPolicy;
    if (startValue.signum() < 0 || plannedYieldRate.compareTo(BigDecimal.ONE.negate()) < 0
        || targetValue.signum() < 0) throw new IllegalArgumentException("Invalid planning bucket");
  }
  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
