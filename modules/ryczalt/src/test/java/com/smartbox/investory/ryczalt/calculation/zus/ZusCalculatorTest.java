package com.smartbox.investory.ryczalt.calculation.zus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZusCalculatorTest {
  private final ZusCalculator calculator = new ZusCalculator();

  @Test
  void calculatesJdgMediumSocialAndHealth() {
    ZusCalculationResult result =
        calculator.calculate(
            new ZusCalculationInput(true, false, "JDG", false, new BigDecimal("60000.01"), null));

    assertAmount("1788.29", result.social());
    assertAmount("830.58", result.health());
    assertAmount("2618.87", result.total());
    assertAmount("1649.82", result.deductibleSocial());
    assertEquals(ZusRules2026.HealthBand.MEDIUM, result.healthBand());
  }

  @Test
  void handlesQualifyingUopAndInactiveJdg() {
    ZusCalculationResult uop =
        calculator.calculate(
            new ZusCalculationInput(true, true, "JDG", false, BigDecimal.ZERO, null));
    ZusCalculationResult inactive =
        calculator.calculate(
            new ZusCalculationInput(false, false, "JDG", false, new BigDecimal("400000"), null));

    assertAmount("0", uop.social());
    assertAmount("498.35", uop.health());
    assertAmount("0", inactive.total());
    assertEquals(ZusRules2026.HealthBand.HIGH, inactive.healthBand());
  }

  @Test
  void includesVoluntarySicknessInSocialAndDeductibleAmount() {
    ZusCalculationResult result =
        calculator.calculate(
            new ZusCalculationInput(true, false, "JDG", true, BigDecimal.ZERO, null));

    assertAmount("1926.76", result.social());
    assertAmount("1788.29", result.deductibleSocial());
    assertEquals(ZusRules2026.VERSION, result.ruleVersion());
  }

  @Test
  void usesPersistedYearSpecificContributionAmountsWhenProvided() {
    ZusCalculationResult result =
        calculator.calculate(
            new ZusCalculationInput(
                true,
                false,
                "JDG",
                false,
                BigDecimal.ZERO,
                new BigDecimal("1646.47"),
                ZusRules2026.HealthBand.HIGH,
                new BigDecimal("1518.98"),
                new BigDecimal("1384.97")));

    assertAmount("1646.47", result.social());
    assertAmount("1384.97", result.health());
    assertAmount("3031.44", result.total());
    assertAmount("1518.98", result.deductibleSocial());
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
