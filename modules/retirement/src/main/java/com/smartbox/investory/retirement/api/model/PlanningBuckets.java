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
    // The normalized Long-Term snapshot is the source of reviewed allocation exposure. Live
    // profile allocations are deliberately ignored here so a reviewed revision is reproducible.
    boolean hasFrozenAssets = !profile.longTermPlanningState().assets().isEmpty();
    BigDecimal bonds =
        hasFrozenAssets
            ? frozenAssetValue(profile, EconomicBucket.FIXED_INCOME)
            : allocation(profile, EconomicBucket.FIXED_INCOME);
    BigDecimal equities = zeroIfNull(profile.investmentCapital());
    BigDecimal realEstate =
        hasFrozenAssets
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
                (bucket == EconomicBucket.FIXED_INCOME
                        && (a.type()
                                == com.smartbox.investory.longterm.api.model.LongTermAssetType.BOND
                            || a.type()
                                == com.smartbox.investory.longterm.api.model.LongTermAssetType
                                    .DEPOSIT))
                    || (bucket == EconomicBucket.REAL_ESTATE
                        && a.type()
                            == com.smartbox.investory.longterm.api.model.LongTermAssetType
                                .REAL_ESTATE))
        .map(a -> zeroIfNull(a.currentValue()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static boolean hasAllocation(InvestmentProfile profile, EconomicBucket bucket) {
    return profile.allocations().stream().anyMatch(a -> a.bucket() == bucket);
  }
}
