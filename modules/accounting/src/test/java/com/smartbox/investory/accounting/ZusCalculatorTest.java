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
    assertThat(result.deductibleSocialContribution()).isEqualByComparingTo("1649.82");
  }

  @Test
  void applies2025SocialAndHighHealthContributionAmounts() {
    var result =
        calculator.calculate(
            new ZusCalculator.Input(
                true,
                false,
                "JDG",
                false,
                new BigDecimal("400000"),
                ZusRules2025.FULL_JDG_SOCIAL,
                ZusRules2026.HealthBand.HIGH),
            ZusRules2025.LABOUR_FUND,
            ZusRules2025.VOLUNTARY_SICKNESS,
            ZusRules2025.HEALTH_HIGH,
            ZusRules2025.VERSION);

    assertThat(result.socialContribution()).isEqualByComparingTo("1646.47");
    assertThat(result.deductibleSocialContribution()).isEqualByComparingTo("1518.98");
    assertThat(result.healthContribution()).isEqualByComparingTo("1384.97");
    assertThat(result.totalObligation()).isEqualByComparingTo("3031.44");
    assertThat(result.ruleVersion()).isEqualTo("ZUS_2025_POC_V1");
  }

  @Test
  void includesVoluntarySicknessInPayableZUSButNotLabourFundInDeduction() {
    var result = calculator.calculate(input(true, false, true, new BigDecimal("100000")));

    assertThat(result.socialContribution()).isEqualByComparingTo("1926.76");
    assertThat(result.deductibleSocialContribution()).isEqualByComparingTo("1788.29");
  }

  @Test
  void selectsHealthBandFromRevenueAfterPaidSocialContributions() {
    assertThat(
            ZusRules2026.healthBandAfterPaidSocial(
                new BigDecimal("61649.82"), new BigDecimal("1649.82")))
        .isEqualTo(ZusRules2026.HealthBand.LOW);
    assertThat(
            ZusRules2026.healthBandAfterPaidSocial(
                new BigDecimal("61649.83"), new BigDecimal("1649.82")))
        .isEqualTo(ZusRules2026.HealthBand.MEDIUM);
  }

  @Test
  void derivesAccountingDatesAndPriorWeekdayFxDate() {
    assertThat(
            AccountingDateRules.accountingPeriod(
                java.time.LocalDate.of(2026, 6, 30),
                java.time.LocalDate.of(2026, 7, 2),
                java.time.LocalDate.of(2026, 7, 5),
                false))
        .isEqualTo(java.time.LocalDate.of(2026, 6, 1));
    assertThat(
            AccountingDateRules.accountingPeriod(
                java.time.LocalDate.of(2026, 7, 31),
                java.time.LocalDate.of(2026, 8, 2),
                null,
                true))
        .isEqualTo(java.time.LocalDate.of(2026, 8, 1));
    assertThat(AccountingDateRules.priorBusinessDay(java.time.LocalDate.of(2026, 8, 3)))
        .isEqualTo(java.time.LocalDate.of(2026, 7, 31));
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
  }

  private ZusCalculator.Input input(boolean jdg, boolean uop, BigDecimal revenue) {
    return input(jdg, uop, false, revenue);
  }

  private ZusCalculator.Input input(
      boolean jdg, boolean uop, boolean voluntarySickness, BigDecimal revenue) {
    return new ZusCalculator.Input(jdg, uop, "JDG", voluntarySickness, revenue, null);
  }

  private ZusCalculator.Input input(String regime, boolean voluntarySickness) {
    return new ZusCalculator.Input(
        true, false, regime, voluntarySickness, new BigDecimal("100000"), null);
  }
}
