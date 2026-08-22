package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

/** Explicit withdrawal and reserve-replenishment policy for retirement projections. */
public record RetirementFundingPolicy(
    BigDecimal reserveTargetYears,
    BigDecimal equityHarvestThresholdRate,
    BigDecimal equityHarvestShare,
    boolean allowEmergencyEquityWithdrawal,
    List<FundingSource> fundingOrder) {
  public static final BigDecimal DEFAULT_RESERVE_TARGET_YEARS = BigDecimal.valueOf(5);
  public static final BigDecimal DEFAULT_HARVEST_THRESHOLD = BigDecimal.valueOf(0.07);
  public static final BigDecimal DEFAULT_HARVEST_SHARE = BigDecimal.valueOf(0.75);
  public static final List<FundingSource> DEFAULT_ORDER =
      List.of(FundingSource.CASH, FundingSource.BONDS, FundingSource.STOCKS);

  public RetirementFundingPolicy {
    reserveTargetYears = nz(reserveTargetYears);
    equityHarvestThresholdRate = nz(equityHarvestThresholdRate);
    equityHarvestShare = nz(equityHarvestShare);
    if (reserveTargetYears.signum() < 0) throw new IllegalArgumentException("Reserve target cannot be negative");
    if (equityHarvestShare.signum() < 0 || equityHarvestShare.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Harvest share must be between 0 and 1");
    fundingOrder = fundingOrder == null || fundingOrder.isEmpty() ? DEFAULT_ORDER : List.copyOf(fundingOrder);
    if (fundingOrder.stream().anyMatch(source -> source == null)
        || new HashSet<>(fundingOrder).size() != fundingOrder.size())
      throw new IllegalArgumentException("Funding order must contain unique sources");
  }

  public static RetirementFundingPolicy defaults() {
    return new RetirementFundingPolicy(DEFAULT_RESERVE_TARGET_YEARS, DEFAULT_HARVEST_THRESHOLD,
        DEFAULT_HARVEST_SHARE, true, DEFAULT_ORDER);
  }

  public static RetirementFundingPolicy fromLegacy(SimulationAssumptions assumptions) {
    return new RetirementFundingPolicy(assumptions.safeReserveYears(),
        assumptions.equityHarvestMinimumReturnRate(), assumptions.equityGainHarvestRate(),
        assumptions.allowEmergencyEquityWithdrawal(), assumptions.fundingOrder());
  }

  /** Domain-neutral names used by the active policy; legacy accessors remain persistence-compatible. */
  public BigDecimal investmentHarvestThresholdRate() { return equityHarvestThresholdRate; }

  public BigDecimal investmentHarvestShare() { return equityHarvestShare; }

  public boolean allowInvestmentWithdrawal() { return allowEmergencyEquityWithdrawal; }

  public List<RetirementFundingSource> economicFundingOrder() {
    return fundingOrder.stream().map(source -> switch (source) {
      case CASH -> RetirementFundingSource.RESERVE;
      case BONDS -> RetirementFundingSource.LONG_TERM;
      case STOCKS -> RetirementFundingSource.INVESTMENT;
    }).toList();
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
