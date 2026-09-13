package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZusCalculatorTest {
  private final ZusCalculator calculator = new ZusCalculator();

  @Test
  void qualifyingUopWaivesOnlyJdgSocialContribution() {
    var result = calculator.calculate(input(true, true, new BigDecimal("400000")));

    assertThat(result.socialContribution()).isZero();
    assertThat(result.healthContribution()).isEqualByComparingTo("1495.04");
    assertThat(result.reason()).isEqualTo("UOP_PRIMARY_INSURANCE");
  }

  @Test
  void jdgOnlyUsesSocialContributionAndRevenueBand() {
    var result = calculator.calculate(input(true, false, new BigDecimal("100000")));

    assertThat(result.socialContribution()).isEqualByComparingTo("1788.29");
    assertThat(result.healthBand()).isEqualTo(ZusRules2026.HealthBand.MEDIUM);
    assertThat(result.totalObligation()).isEqualByComparingTo("2618.87");
  }

  @Test
  void inactiveJdgHasNoZusObligation() {
    var result = calculator.calculate(input(false, false, new BigDecimal("100000")));

    assertThat(result.totalObligation()).isZero();
  }

  @Test
  void rejectsInputsOutsideTheSupportedZusPolicy() {
    assertThatThrownBy(() -> calculator.calculate(input("PREFERENTIAL", false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported ZUS regime");
    assertThatThrownBy(() -> calculator.calculate(input("JDG", true)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Voluntary sickness");
  }

  private ZusCalculator.Input input(boolean jdg, boolean uop, BigDecimal revenue) {
    return new ZusCalculator.Input(jdg, uop, "JDG", false, revenue, null);
  }

  private ZusCalculator.Input input(String regime, boolean voluntarySickness) {
    return new ZusCalculator.Input(
        true, false, regime, voluntarySickness, new BigDecimal("100000"), null);
  }
}
