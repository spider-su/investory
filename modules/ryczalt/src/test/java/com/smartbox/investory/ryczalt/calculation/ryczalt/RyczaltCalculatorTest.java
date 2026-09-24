package com.smartbox.investory.ryczalt.calculation.ryczalt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RyczaltCalculatorTest {
  private final RyczaltCalculator calculator = new RyczaltCalculator();

  @Test
  void calculatesSingleRateRevenue() {
    RyczaltCalculationResult result =
        calculator.calculate(
            new RyczaltCalculationInput(
                Map.of(new BigDecimal("0.12"), new BigDecimal("10000")),
                BigDecimal.ZERO,
                BigDecimal.ZERO));

    assertAmount("10000", result.revenue());
    assertAmount("10000", result.taxableBase());
    assertAmount("1200", result.calculatedTax());
    assertEquals(RyczaltRules2026.VERSION, result.ruleVersion());
  }

  @Test
  void allocationIsDeterministicAndIndependentOfInputOrder() {
    Map<BigDecimal, BigDecimal> first = new LinkedHashMap<>();
    first.put(new BigDecimal("0.12"), new BigDecimal("200"));
    first.put(new BigDecimal("0.03"), new BigDecimal("100"));
    Map<BigDecimal, BigDecimal> second = new LinkedHashMap<>();
    second.put(new BigDecimal("0.03"), new BigDecimal("100"));
    second.put(new BigDecimal("0.12"), new BigDecimal("200"));

    RyczaltCalculationResult left =
        calculator.calculate(
            new RyczaltCalculationInput(first, new BigDecimal("10"), BigDecimal.ZERO));
    RyczaltCalculationResult right =
        calculator.calculate(
            new RyczaltCalculationInput(second, new BigDecimal("10"), BigDecimal.ZERO));

    assertEquals(left.taxableByRate(), right.taxableByRate());
    assertEquals(left.taxByRate(), right.taxByRate());
    assertAmount("290", left.taxableBase());
    assertAmount("26", left.calculatedTax());
  }

  @Test
  void carriesUnusedDeductionForward() {
    RyczaltCalculationResult result =
        calculator.calculate(
            new RyczaltCalculationInput(
                Map.of(new BigDecimal("0.12"), new BigDecimal("100")),
                new BigDecimal("150"),
                BigDecimal.ZERO));

    assertAmount("150", result.deductionsAvailable());
    assertAmount("100", result.deductionsUsed());
    assertAmount("50", result.deductionsCarryForward());
    assertAmount("0", result.taxableBase());
  }

  @Test
  void appliesHalfOfPaidHealthContributionAsDeduction() {
    RyczaltCalculationResult result =
        calculator.calculate(
            new RyczaltCalculationInput(
                Map.of(new BigDecimal("0.12"), new BigDecimal("1000")),
                BigDecimal.ZERO,
                new BigDecimal("100")));

    assertAmount("50.00", result.healthDeduction());
    assertAmount("950", result.taxableBase());
    assertAmount("114", result.calculatedTax());
  }

  @Test
  void matchesJanuaryWfirmaHealthDeductionAndRyczalt() {
    RyczaltCalculationResult result =
        calculator.calculate(
            new RyczaltCalculationInput(
                Map.of(new BigDecimal("0.12"), new BigDecimal("61771.23")),
                BigDecimal.ZERO,
                new BigDecimal("1384.98")));

    assertAmount("692.49", result.healthDeduction());
    assertAmount("61079", result.taxableBase());
    assertAmount("7329", result.calculatedTax());
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
