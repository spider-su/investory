package com.smartbox.investory.retirement.simulation;
import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;


import java.math.BigDecimal;

/** Allocates operating surplus without inspecting Investment or Long-Term internals. */
public final class RetirementSurplusAllocator {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public Result allocate(BigDecimal cashSurplus, BigDecimal reserveBefore,
      BigDecimal reserveTarget, SurplusPolicy policy) {
    BigDecimal surplus = zeroIfNull(cashSurplus).max(ZERO);
    BigDecimal refill = policy == SurplusPolicy.KEEP_UNALLOCATED ? ZERO
        : surplus.min(zeroIfNull(reserveTarget).subtract(zeroIfNull(reserveBefore)).max(ZERO));
    BigDecimal invest = policy == SurplusPolicy.REFILL_RESERVE_THEN_INVEST
        ? surplus.subtract(refill) : ZERO;
    BigDecimal unallocated = surplus.subtract(refill).subtract(invest);
    return new Result(surplus, refill, invest, unallocated);
  }

  

  public record Result(BigDecimal cashSurplus, BigDecimal reserveRefill,
      BigDecimal investmentContribution, BigDecimal unallocatedSurplus) {}
}
