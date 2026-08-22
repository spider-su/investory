package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

/** Allocates operating surplus without inspecting Investment or Long-Term internals. */
public final class RetirementSurplusAllocator {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public Result allocate(BigDecimal cashSurplus, BigDecimal reserveBefore,
      BigDecimal reserveTarget, SurplusPolicy policy) {
    BigDecimal surplus = nz(cashSurplus).max(ZERO);
    BigDecimal refill = policy == SurplusPolicy.KEEP_UNALLOCATED ? ZERO
        : surplus.min(nz(reserveTarget).subtract(nz(reserveBefore)).max(ZERO));
    BigDecimal invest = policy == SurplusPolicy.REFILL_RESERVE_THEN_INVEST
        ? surplus.subtract(refill) : ZERO;
    BigDecimal unallocated = surplus.subtract(refill).subtract(invest);
    return new Result(surplus, refill, invest, unallocated);
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? ZERO : value; }

  public record Result(BigDecimal cashSurplus, BigDecimal reserveRefill,
      BigDecimal investmentContribution, BigDecimal unallocatedSurplus) {}
}
