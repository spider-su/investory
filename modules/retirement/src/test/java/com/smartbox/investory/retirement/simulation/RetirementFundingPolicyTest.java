package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RetirementFundingPolicyTest {
  @Test
  void defaultsExposeAnExplicitDefensivePolicy() {
    var policy = RetirementFundingPolicy.defaults();

    assertThat(policy.reserveTargetYears()).isEqualByComparingTo("5");
    assertThat(policy.equityHarvestThresholdRate()).isEqualByComparingTo("0.07");
    assertThat(policy.equityHarvestShare()).isEqualByComparingTo("0.75");
    assertThat(policy.allowEmergencyEquityWithdrawal()).isTrue();
    assertThat(policy.fundingOrder())
        .containsExactly(FundingSource.CASH, FundingSource.BONDS, FundingSource.STOCKS);
  }

  @Test
  void harvestShareMustBeWithinBounds() {
    assertThatThrownBy(
            () ->
                new RetirementFundingPolicy(
                    BigDecimal.ONE,
                    BigDecimal.ZERO,
                    new BigDecimal("1.01"),
                    true,
                    RetirementFundingPolicy.DEFAULT_ORDER))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
