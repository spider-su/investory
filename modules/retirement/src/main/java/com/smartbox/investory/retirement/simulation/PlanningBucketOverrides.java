package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

/** Optional sandbox values. Null means use the frozen baseline value. */
public record PlanningBucketOverrides(boolean sandboxMode, BigDecimal cashStart, BigDecimal bondStart,
    BigDecimal bondYield, BigDecimal equityStart, BigDecimal equityYield, BigDecimal realEstateStart,
    BigDecimal rentalCashIncome, BigDecimal bondTarget) {
  public PlanningBuckets apply(PlanningBuckets baseline) {
    if (!sandboxMode) return baseline;
    return PlanningBuckets.of(value(cashStart, baseline.cash().startValue()), value(bondStart, baseline.bonds().startValue()),
        value(equityStart, baseline.equities().startValue()), value(realEstateStart, baseline.realEstate().startValue()),
        value(bondYield, baseline.bonds().plannedYieldRate()), value(equityYield, baseline.equities().plannedYieldRate()),
        value(bondTarget, baseline.bonds().targetValue()), value(rentalCashIncome, baseline.rentalCashIncome()));
  }
  private static BigDecimal value(BigDecimal override, BigDecimal fallback) { return override == null ? fallback : override; }
}
