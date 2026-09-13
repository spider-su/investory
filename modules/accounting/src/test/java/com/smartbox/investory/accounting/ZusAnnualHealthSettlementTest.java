package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZusAnnualHealthSettlementTest {
  @Test
  void detectsAdditionalPaymentForFinalHealthBand() {
    var settlement =
        new ZusAnnualHealthSettlement(
            new BigDecimal("61000"),
            ZusRules2026.HealthBand.MEDIUM,
            new BigDecimal("9000"),
            null,
            null);

    assertThat(settlement.requiredAnnualContribution()).isEqualByComparingTo("9966.96");
    assertThat(settlement.difference()).isEqualByComparingTo("-966.96");
    assertThat(settlement.additionalPaymentRequired()).isTrue();
    assertThat(settlement.overpayment()).isFalse();
  }
}
