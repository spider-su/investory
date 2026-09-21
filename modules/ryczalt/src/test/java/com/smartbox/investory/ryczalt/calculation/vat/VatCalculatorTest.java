package com.smartbox.investory.ryczalt.calculation.vat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VatCalculatorTest {
  private final VatCalculator calculator = new VatCalculator();

  @Test
  void settlesOutputCorrectionAndInputVatAtWholePlnBoundary() {
    VatCalculationResult result =
        calculator.calculate(
            new VatCalculationInput(
                new BigDecimal("1234.49"), BigDecimal.ZERO, new BigDecimal("1000.51")));

    assertAmount("1234.49", result.outputVat());
    assertAmount("233", result.calculatedVat());
  }

  @Test
  void appliesExplicitAdjustmentAndNeverReturnsNegativePayableVat() {
    VatCalculationResult adjusted =
        calculator.calculate(
            new VatCalculationInput(
                new BigDecimal("12.49"), new BigDecimal("0.01"), BigDecimal.ZERO, BigDecimal.ZERO));
    VatCalculationResult excessInput =
        calculator.calculate(
            new VatCalculationInput(new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("11")));

    assertAmount("13", adjusted.calculatedVat());
    assertAmount("0", excessInput.calculatedVat());
    assertEquals(VatRules2026.VERSION, adjusted.ruleVersion());
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
