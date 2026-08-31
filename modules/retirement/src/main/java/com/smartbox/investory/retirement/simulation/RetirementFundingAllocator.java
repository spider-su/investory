package com.smartbox.investory.retirement.simulation;
import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;


import java.math.BigDecimal;

/** Small policy service for the reserve portion of yearly withdrawal allocation. */
public final class RetirementFundingAllocator {
  public Allocation allocateReserve(BigDecimal fundingGap, BigDecimal reserveAvailable,
      RetirementFundingPolicy policy) {
    BigDecimal gap = zeroIfNull(fundingGap).max(BigDecimal.ZERO);
    BigDecimal reserve = zeroIfNull(reserveAvailable).max(BigDecimal.ZERO);
    BigDecimal withdrawal = reserve.min(gap);
    return new Allocation(withdrawal, gap.subtract(withdrawal).max(BigDecimal.ZERO));
  }

  public record Allocation(BigDecimal reserveWithdrawal, BigDecimal remainingGap) {}

  
}
