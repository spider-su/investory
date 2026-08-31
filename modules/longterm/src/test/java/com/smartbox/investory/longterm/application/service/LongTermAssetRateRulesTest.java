package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LongTermAssetRateRulesTest {

  @Test
  void acceptsCanonicalDecimalRates() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              LongTermAssetRateRules.requireReturnRate(new BigDecimal("0.085"), "Tax rate");
              LongTermAssetRateRules.requireGrowthRate(new BigDecimal("-0.25"));
              LongTermAssetRateRules.requireGrowthRate(BigDecimal.ONE);
            });
  }

  @Test
  void rejectsPercentagePointsAtApplicationBoundary() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> LongTermAssetRateRules.requireReturnRate(new BigDecimal("8.5"), "Tax rate"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> LongTermAssetRateRules.requireGrowthRate(new BigDecimal("4.25")));
  }
}
