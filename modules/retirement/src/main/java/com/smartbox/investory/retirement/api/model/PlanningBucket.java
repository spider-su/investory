package com.smartbox.investory.retirement.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import java.math.BigDecimal;

/** Immutable aggregate planning state. Instrument mechanics do not cross this boundary. */
public record PlanningBucket(
    EconomicBucket type,
    BigDecimal startValue,
    BigDecimal plannedYieldRate,
    int spendingPriority,
    BigDecimal targetValue,
    RefillPolicy refillPolicy) {
  public PlanningBucket {
    type = type == null ? EconomicBucket.LIQUID_CASH : type;
    startValue = zeroIfNull(startValue);
    plannedYieldRate = zeroIfNull(plannedYieldRate);
    targetValue = zeroIfNull(targetValue);
    refillPolicy = refillPolicy == null ? RefillPolicy.NONE : refillPolicy;
    if (startValue.signum() < 0
        || plannedYieldRate.compareTo(BigDecimal.ONE.negate()) < 0
        || targetValue.signum() < 0) throw new IllegalArgumentException("Invalid planning bucket");
  }
}
