package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

/** Small policy service for the reserve portion of yearly withdrawal allocation. */
public final class RetirementFundingAllocator {
  public Allocation allocateReserve(BigDecimal fundingGap, BigDecimal reserveAvailable,
      RetirementFundingPolicy policy) {
    BigDecimal gap = nz(fundingGap).max(BigDecimal.ZERO);
    BigDecimal reserve = nz(reserveAvailable).max(BigDecimal.ZERO);
    BigDecimal withdrawal = reserve.min(gap);
    return new Allocation(withdrawal, gap.subtract(withdrawal).max(BigDecimal.ZERO));
  }

  public record Allocation(BigDecimal reserveWithdrawal, BigDecimal remainingGap) {}

  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
