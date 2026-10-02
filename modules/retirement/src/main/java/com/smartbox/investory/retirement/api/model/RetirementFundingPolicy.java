package com.smartbox.investory.retirement.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import java.math.BigDecimal;
import java.util.List;

/** Explicit withdrawal and reserve-replenishment policy for retirement projections. */
public record RetirementFundingPolicy(
    SimulationFundingStrategy fundingStrategy,
    BigDecimal reserveTargetYears,
    BigDecimal equityHarvestThresholdRate,
    BigDecimal equityHarvestShare,
    boolean allowEmergencyEquityWithdrawal,
    List<RetirementFundingSource> fundingOrder) {
  public static final BigDecimal DEFAULT_RESERVE_TARGET_YEARS = BigDecimal.valueOf(5);
  public static final BigDecimal DEFAULT_HARVEST_THRESHOLD = BigDecimal.valueOf(0.07);
  public static final BigDecimal DEFAULT_HARVEST_SHARE = BigDecimal.valueOf(0.75);
  public static final List<RetirementFundingSource> DEFAULT_ORDER =
      List.of(
          RetirementFundingSource.RESERVE,
          RetirementFundingSource.LONG_TERM,
          RetirementFundingSource.INVESTMENT);

  public RetirementFundingPolicy {
    fundingStrategy = canonicalStrategy(fundingStrategy);
    reserveTargetYears = zeroIfNull(reserveTargetYears);
    equityHarvestThresholdRate = zeroIfNull(equityHarvestThresholdRate);
    equityHarvestShare = zeroIfNull(equityHarvestShare);
    if (reserveTargetYears.signum() < 0)
      throw new IllegalArgumentException("Reserve target cannot be negative");
    if (equityHarvestShare.signum() < 0 || equityHarvestShare.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Harvest share must be between 0 and 1");
    // Retained for persistence/API compatibility. The POC engine has one fixed funding order.
    fundingOrder = DEFAULT_ORDER;
  }

  private static SimulationFundingStrategy canonicalStrategy(SimulationFundingStrategy strategy) {
    if (strategy == null) return SimulationFundingStrategy.RESERVE_AND_HARVEST;
    return switch (strategy) {
      case RESERVE_AND_HARVEST, SIMPLE_WATERFALL -> SimulationFundingStrategy.RESERVE_AND_HARVEST;
      default -> throw new IllegalArgumentException("Unsupported simulation funding strategy");
    };
  }

  public static RetirementFundingPolicy defaults() {
    return new RetirementFundingPolicy(
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        DEFAULT_RESERVE_TARGET_YEARS,
        DEFAULT_HARVEST_THRESHOLD,
        DEFAULT_HARVEST_SHARE,
        true,
        DEFAULT_ORDER);
  }

  /** Domain-neutral names used by the active policy. */
  public BigDecimal investmentHarvestThresholdRate() {
    return equityHarvestThresholdRate;
  }

  public BigDecimal investmentHarvestShare() {
    return equityHarvestShare;
  }

  public boolean allowInvestmentWithdrawal() {
    return allowEmergencyEquityWithdrawal;
  }
}
