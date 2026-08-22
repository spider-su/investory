package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/** Frozen four-bucket starting state and its cash-income assumptions. */
public record PlanningBuckets(PlanningBucket cash, PlanningBucket bonds, PlanningBucket equities,
    PlanningBucket realEstate, BigDecimal rentalCashIncome, BigDecimal realEstateGrowthRate) {
  public PlanningBuckets {
    cash = cash == null ? new PlanningBucket(BucketType.CASH, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, RefillPolicy.NONE) : cash;
    bonds = bonds == null ? new PlanningBucket(BucketType.BONDS, BigDecimal.ZERO, BigDecimal.ZERO, 2, BigDecimal.ZERO, RefillPolicy.NONE) : bonds;
    equities = equities == null ? new PlanningBucket(BucketType.EQUITIES, BigDecimal.ZERO, BigDecimal.ZERO, 3, BigDecimal.ZERO, RefillPolicy.EQUITY_HARVEST) : equities;
    realEstate = realEstate == null ? new PlanningBucket(BucketType.REAL_ESTATE, BigDecimal.ZERO, BigDecimal.ZERO, 4, BigDecimal.ZERO, RefillPolicy.NONE) : realEstate;
    rentalCashIncome = nz(rentalCashIncome); realEstateGrowthRate = nz(realEstateGrowthRate);
  }
  public Map<BucketType, PlanningBucket> asMap() {
    var result = new EnumMap<BucketType, PlanningBucket>(BucketType.class);
    result.put(BucketType.CASH, cash); result.put(BucketType.BONDS, bonds);
    result.put(BucketType.EQUITIES, equities); result.put(BucketType.REAL_ESTATE, realEstate);
    return Map.copyOf(result);
  }
  public static PlanningBuckets of(BigDecimal cash, BigDecimal bonds, BigDecimal equities,
      BigDecimal realEstate, BigDecimal bondYield, BigDecimal equityYield, BigDecimal bondTarget,
      BigDecimal rentalIncome) {
    return new PlanningBuckets(
        new PlanningBucket(BucketType.CASH, cash, BigDecimal.ZERO, 1, BigDecimal.ZERO, RefillPolicy.NONE),
        new PlanningBucket(BucketType.BONDS, bonds, bondYield, 2, bondTarget, RefillPolicy.NONE),
        new PlanningBucket(BucketType.EQUITIES, equities, equityYield, 3, BigDecimal.ZERO, RefillPolicy.EQUITY_HARVEST),
        new PlanningBucket(BucketType.REAL_ESTATE, realEstate, BigDecimal.ZERO, 4, BigDecimal.ZERO, RefillPolicy.NONE),
        rentalIncome, BigDecimal.ZERO);
  }
  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
