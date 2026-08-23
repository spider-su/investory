package com.smartbox.investory.retirement.simulation;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/** Frozen four-bucket starting state and its cash-income assumptions. */
public record PlanningBuckets(
    PlanningBucket cash,
    PlanningBucket bonds,
    PlanningBucket equities,
    PlanningBucket realEstate,
    BigDecimal rentalCashIncome,
    BigDecimal realEstateGrowthRate) {
  public PlanningBuckets {
    cash =
        cash == null
            ? new PlanningBucket(
                BucketType.CASH,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                BigDecimal.ZERO,
                RefillPolicy.NONE)
            : cash;
    bonds =
        bonds == null
            ? new PlanningBucket(
                BucketType.BONDS,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2,
                BigDecimal.ZERO,
                RefillPolicy.NONE)
            : bonds;
    equities =
        equities == null
            ? new PlanningBucket(
                BucketType.EQUITIES,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                3,
                BigDecimal.ZERO,
                RefillPolicy.EQUITY_HARVEST)
            : equities;
    realEstate =
        realEstate == null
            ? new PlanningBucket(
                BucketType.REAL_ESTATE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                4,
                BigDecimal.ZERO,
                RefillPolicy.NONE)
            : realEstate;
    rentalCashIncome = zeroIfNull(rentalCashIncome);
    realEstateGrowthRate = zeroIfNull(realEstateGrowthRate);
  }

  public Map<BucketType, PlanningBucket> asMap() {
    var result = new EnumMap<BucketType, PlanningBucket>(BucketType.class);
    result.put(BucketType.CASH, cash);
    result.put(BucketType.BONDS, bonds);
    result.put(BucketType.EQUITIES, equities);
    result.put(BucketType.REAL_ESTATE, realEstate);
    return Map.copyOf(result);
  }

  public static PlanningBuckets of(
      BigDecimal cash,
      BigDecimal bonds,
      BigDecimal equities,
      BigDecimal realEstate,
      BigDecimal bondYield,
      BigDecimal equityYield,
      BigDecimal bondTarget,
      BigDecimal rentalIncome) {
    return new PlanningBuckets(
        new PlanningBucket(
            BucketType.CASH, cash, BigDecimal.ZERO, 1, BigDecimal.ZERO, RefillPolicy.NONE),
        new PlanningBucket(BucketType.BONDS, bonds, bondYield, 2, bondTarget, RefillPolicy.NONE),
        new PlanningBucket(
            BucketType.EQUITIES,
            equities,
            equityYield,
            3,
            BigDecimal.ZERO,
            RefillPolicy.EQUITY_HARVEST),
        new PlanningBucket(
            BucketType.REAL_ESTATE,
            realEstate,
            BigDecimal.ZERO,
            4,
            BigDecimal.ZERO,
            RefillPolicy.NONE),
        rentalIncome,
        BigDecimal.ZERO);
  }

  /** Compatibility mapper; derives the BASE Bond yield from the reviewed source snapshot. */
  public static PlanningBuckets fromProfile(
      InvestmentProfile profile,
      BigDecimal equityYield,
      BigDecimal fallbackBondYield,
      int baselineYear) {
    return fromProfileWithBondYield(
        profile, equityYield, baseBondYield(profile, fallbackBondYield, baselineYear));
  }

  /** Maps the reviewed source snapshot once, with an explicit normalized future Bond yield. */
  public static PlanningBuckets fromProfileWithBondYield(
      InvestmentProfile profile, BigDecimal equityYield, BigDecimal bondYield) {
    BigDecimal bonds = allocation(profile, EconomicBucket.FIXED_INCOME);
    if (!hasAllocation(profile, EconomicBucket.FIXED_INCOME))
      bonds =
          profile.longTermAssets().stream()
              .filter(a -> a.bucket() == EconomicBucket.FIXED_INCOME)
              .map(a -> zeroIfNull(a.currentValue()))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal equities = allocation(profile, EconomicBucket.EQUITY);
    if (!hasAllocation(profile, EconomicBucket.EQUITY))
      equities = zeroIfNull(profile.investmentCapital());
    BigDecimal realEstate = allocation(profile, EconomicBucket.REAL_ESTATE);
    if (!hasAllocation(profile, EconomicBucket.REAL_ESTATE))
      realEstate =
          profile.longTermAssets().stream()
              .filter(a -> a.bucket() == EconomicBucket.REAL_ESTATE)
              .map(a -> zeroIfNull(a.currentValue()))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    return of(
        zeroIfNull(profile.retirementReserve()),
        bonds,
        equities,
        realEstate,
        zeroIfNull(bondYield),
        zeroIfNull(equityYield),
        bonds,
        profile.currentRentalIncome());
  }

  /** Derives the normalized BASE bond yield from the frozen reviewed source state. */
  public static BigDecimal baseBondYield(
      InvestmentProfile profile, BigDecimal fallbackBondYield, int baselineYear) {
    return new FrozenBondCashFlowProjection()
        .baseCapitalizedBondYield(profile, fallbackBondYield, baselineYear);
  }

  /** True when the frozen source state contains enough data to derive a bond yield. */
  public static boolean hasSourceBondYield(InvestmentProfile profile, int baselineYear) {
    return new FrozenBondCashFlowProjection().hasCapitalizedBondYield(profile, baselineYear);
  }

  private static BigDecimal allocation(InvestmentProfile profile, EconomicBucket bucket) {
    return profile.allocations().stream()
        .filter(a -> a.bucket() == bucket)
        .map(a -> zeroIfNull(a.value()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static boolean hasAllocation(InvestmentProfile profile, EconomicBucket bucket) {
    return profile.allocations().stream().anyMatch(a -> a.bucket() == bucket);
  }
}
