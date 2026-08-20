package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.EconomicBucket;
import java.math.BigDecimal;
import java.util.EnumMap;

/** Applies one deterministic, plan-configured withdrawal order to market buckets. */
public final class FundingAllocator {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private FundingAllocator() {}

  public static Result fund(
      EnumMap<EconomicBucket, BigDecimal> source,
      BigDecimal amount,
      SimulationAssumptions assumptions) {
    EnumMap<EconomicBucket, BigDecimal> balances = new EnumMap<>(source);
    BigDecimal left = amount.max(ZERO);
    BigDecimal stocksUsed = ZERO;
    for (FundingSource fundingSource : assumptions.fundingOrder()) {
      if (fundingSource == FundingSource.STOCKS
          && assumptions.fundingStrategy() != SimulationFundingStrategy.SIMPLE_WATERFALL
          && !assumptions.allowEmergencyEquityWithdrawal()) continue;
      EconomicBucket bucket = bucket(fundingSource);
      BigDecimal available = balances.getOrDefault(bucket, ZERO);
      BigDecimal used = available.min(left).max(ZERO);
      balances.put(bucket, available.subtract(used));
      left = left.subtract(used);
      if (fundingSource == FundingSource.STOCKS) stocksUsed = stocksUsed.add(used);
      if (left.signum() == 0) break;
    }
    return new Result(balances, left, stocksUsed);
  }

  private static EconomicBucket bucket(FundingSource source) {
    return switch (source) {
      case CASH -> EconomicBucket.LIQUID_CASH;
      case BONDS -> EconomicBucket.FIXED_INCOME;
      case STOCKS -> EconomicBucket.EQUITY;
    };
  }

  public record Result(
      EnumMap<EconomicBucket, BigDecimal> balances,
      BigDecimal unfundedAmount,
      BigDecimal stocksWithdrawal) {}
}
