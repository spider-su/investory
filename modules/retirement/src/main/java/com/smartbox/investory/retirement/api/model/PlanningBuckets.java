package com.smartbox.investory.retirement.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
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
                EconomicBucket.LIQUID_CASH,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                BigDecimal.ZERO,
                RefillPolicy.NONE)
            : cash;
    bonds =
        bonds == null
            ? new PlanningBucket(
                EconomicBucket.FIXED_INCOME,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2,
                BigDecimal.ZERO,
                RefillPolicy.NONE)
            : bonds;
    equities =
        equities == null
            ? new PlanningBucket(
                EconomicBucket.EQUITY,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                3,
                BigDecimal.ZERO,
                RefillPolicy.EQUITY_HARVEST)
            : equities;
    realEstate =
        realEstate == null
            ? new PlanningBucket(
                EconomicBucket.REAL_ESTATE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                4,
                BigDecimal.ZERO,
                RefillPolicy.NONE)
            : realEstate;
    rentalCashIncome = zeroIfNull(rentalCashIncome);
    realEstateGrowthRate = zeroIfNull(realEstateGrowthRate);
  }

  public Map<EconomicBucket, PlanningBucket> asMap() {
    var result = new EnumMap<EconomicBucket, PlanningBucket>(EconomicBucket.class);
    result.put(EconomicBucket.LIQUID_CASH, cash);
    result.put(EconomicBucket.FIXED_INCOME, bonds);
    result.put(EconomicBucket.EQUITY, equities);
    result.put(EconomicBucket.REAL_ESTATE, realEstate);
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
            EconomicBucket.LIQUID_CASH,
            cash,
            BigDecimal.ZERO,
            1,
            BigDecimal.ZERO,
            RefillPolicy.NONE),
        new PlanningBucket(
            EconomicBucket.FIXED_INCOME, bonds, bondYield, 2, bondTarget, RefillPolicy.NONE),
        new PlanningBucket(
            EconomicBucket.EQUITY,
            equities,
            equityYield,
            3,
            BigDecimal.ZERO,
            RefillPolicy.EQUITY_HARVEST),
        new PlanningBucket(
            EconomicBucket.REAL_ESTATE,
            realEstate,
            BigDecimal.ZERO,
            4,
            BigDecimal.ZERO,
            RefillPolicy.NONE),
        rentalIncome,
        BigDecimal.ZERO);
  }

  /** Maps factual CURRENT profile state and derives the BASE Bond yield from that state. */
  public static PlanningBuckets fromLiveProfile(
      InvestmentProfile profile,
      BigDecimal equityYield,
      BigDecimal fallbackBondYield,
      int baselineYear) {
    return fromLiveProfileWithBondYield(
        profile, equityYield, baseBondYield(profile, fallbackBondYield, baselineYear));
  }

  /** Maps factual CURRENT profile state with an explicit normalized Bond yield. */
  public static PlanningBuckets fromLiveProfileWithBondYield(
      InvestmentProfile profile, BigDecimal equityYield, BigDecimal bondYield) {
    return fromProfile(profile, equityYield, bondYield);
  }

  /** Maps reviewed forward profile state with an explicit normalized Bond yield. */
  public static PlanningBuckets fromReviewedProfileWithBondYield(
      InvestmentProfile profile, BigDecimal equityYield, BigDecimal bondYield) {
    BigDecimal bonds =
        hasFrozenAsset(profile, EconomicBucket.FIXED_INCOME)
            ? frozenAssetValue(profile, EconomicBucket.FIXED_INCOME)
            : allocation(profile, EconomicBucket.FIXED_INCOME);
    BigDecimal equities = zeroIfNull(profile.investmentCapital());
    BigDecimal realEstate =
        hasFrozenAsset(profile, EconomicBucket.REAL_ESTATE)
            ? frozenAssetValue(profile, EconomicBucket.REAL_ESTATE)
            : allocation(profile, EconomicBucket.REAL_ESTATE);
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

  private static PlanningBuckets fromProfile(
      InvestmentProfile profile, BigDecimal equityYield, BigDecimal bondYield) {
    // Long-Term owns the normalized reviewed facts for the assets it supplies. The profile
    // allocation still supplies a bucket when that bucket has no reviewed Long-Term asset (for
    // example, a market-held bond alongside a reviewed real-estate asset). Resolve each bucket
    // independently; one source bucket must not erase another source domain's exposure.
    BigDecimal bonds =
        hasFrozenAsset(profile, EconomicBucket.FIXED_INCOME)
            ? frozenAssetValue(profile, EconomicBucket.FIXED_INCOME)
            : allocation(profile, EconomicBucket.FIXED_INCOME);
    BigDecimal equities = zeroIfNull(profile.investmentCapital());
    BigDecimal realEstate =
        hasFrozenAsset(profile, EconomicBucket.REAL_ESTATE)
            ? frozenAssetValue(profile, EconomicBucket.REAL_ESTATE)
            : allocation(profile, EconomicBucket.REAL_ESTATE);
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

  private static BigDecimal frozenAssetValue(InvestmentProfile profile, EconomicBucket bucket) {
    return profile.longTermPlanningState().assets().stream()
        .filter(
            a ->
                (bucket == EconomicBucket.FIXED_INCOME && a.bucket() == EconomicBucket.FIXED_INCOME)
                    || (bucket == EconomicBucket.REAL_ESTATE
                        && a.bucket() == EconomicBucket.REAL_ESTATE))
        .map(a -> zeroIfNull(a.currentValue()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static boolean hasFrozenAsset(InvestmentProfile profile, EconomicBucket bucket) {
    return profile.longTermPlanningState().assets().stream()
        .anyMatch(asset -> asset.bucket() == bucket);
  }
}
