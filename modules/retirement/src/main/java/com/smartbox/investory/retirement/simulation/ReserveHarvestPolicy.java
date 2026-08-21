package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Shared reserve target and positive-equity-gain harvest policy. */
public final class ReserveHarvestPolicy {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private ReserveHarvestPolicy() {}

  public static BigDecimal eligibleEquityHarvest(
      EnumMap<EconomicBucket, BigDecimal> marketEnd,
      BigDecimal equityGain,
      SimulationScenarioSettings settings,
      SimulationAssumptions assumptions) {
    if (assumptions.fundingStrategy() != SimulationFundingStrategy.RESERVE_AND_HARVEST
        || settings.equityReturnRate().compareTo(assumptions.equityHarvestMinimumReturnRate()) < 0)
      return ZERO;
    return equityGain
        .multiply(assumptions.equityGainHarvestRate())
        .min(marketEnd.getOrDefault(EconomicBucket.EQUITY, ZERO))
        .max(ZERO);
  }

  public static BigDecimal harvestBondDeficit(
      EnumMap<EconomicBucket, BigDecimal> marketEnd,
      BigDecimal eligibleHarvest,
      BigDecimal reserveTarget,
      BigDecimal manualCash,
      List<ProjectedLongTermAsset> assets,
      Map<Long, BigDecimal> values,
      SimulationAssumptions assumptions,
      boolean retired) {
    if (!retired || assumptions.fundingStrategy() != SimulationFundingStrategy.RESERVE_AND_HARVEST)
      return ZERO;
    BigDecimal deficit =
        reserveTarget
            .subtract(
                defensiveReserve(
                    marketEnd.getOrDefault(EconomicBucket.LIQUID_CASH, ZERO),
                    manualCash,
                    assets,
                    values))
            .max(ZERO);
    BigDecimal harvest =
        deficit.min(eligibleHarvest).min(marketEnd.getOrDefault(EconomicBucket.EQUITY, ZERO));
    marketEnd.merge(EconomicBucket.EQUITY, harvest.negate(), BigDecimal::add);
    return harvest;
  }

  public static BigDecimal defensiveReserve(
      BigDecimal marketCash,
      BigDecimal manualCash,
      List<ProjectedLongTermAsset> assets,
      Map<Long, BigDecimal> values) {
    return marketCash.add(manualCash).add(bondValue(assets, values));
  }

  public static BigDecimal bondValue(
      List<ProjectedLongTermAsset> assets, Map<Long, BigDecimal> values) {
    return assets.stream()
        .filter(asset -> asset.type() == LongTermAssetType.BOND)
        .map(asset -> values.getOrDefault(asset.id(), ZERO))
        .reduce(ZERO, BigDecimal::add);
  }
}
