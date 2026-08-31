package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Surplus Allocator")
class RetirementSurplusAllocatorTest {
  private final RetirementSurplusAllocator allocator = new RetirementSurplusAllocator();

  @DisplayName("keeps Surplus Unallocated")
  @Test
  void keepsSurplusUnallocated() {
    var r =
        allocator.allocate(
            new BigDecimal("50000"),
            new BigDecimal("70000"),
            new BigDecimal("100000"),
            SurplusPolicy.KEEP_UNALLOCATED);
    assertThat(r.reserveRefill()).isZero();
    assertThat(r.investmentContribution()).isZero();
    assertThat(r.unallocatedSurplus()).isEqualByComparingTo("50000");
  }

  @DisplayName("refills Reserve Only To Target")
  @Test
  void refillsReserveOnlyToTarget() {
    var r =
        allocator.allocate(
            new BigDecimal("50000"),
            new BigDecimal("70000"),
            new BigDecimal("100000"),
            SurplusPolicy.REFILL_RESERVE);
    assertThat(r.reserveRefill()).isEqualByComparingTo("30000");
    assertThat(r.unallocatedSurplus()).isEqualByComparingTo("20000");
  }

  @DisplayName("invests After Reserve Refill")
  @Test
  void investsAfterReserveRefill() {
    var r =
        allocator.allocate(
            new BigDecimal("50000"),
            new BigDecimal("70000"),
            new BigDecimal("100000"),
            SurplusPolicy.REFILL_RESERVE_THEN_INVEST);
    assertThat(r.reserveRefill()).isEqualByComparingTo("30000");
    assertThat(r.investmentContribution()).isEqualByComparingTo("20000");
    assertThat(r.unallocatedSurplus()).isZero();
  }
}
