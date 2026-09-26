package com.smartbox.investory.ryczalt.calculation;

import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationInput;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationInput;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationInput;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Small calculator-level February fixture. The complete Jan-Jul story lives in test-support.
 *
 * <p>The module copies only normalized values and has no dependency on test-support.
 */
final class February2026CalculatorFixture {
  static final RyczaltCalculationInput FEBRUARY_RYCZALT =
      new RyczaltCalculationInput(
          Map.of(new BigDecimal("0.12"), new BigDecimal("29600")),
          BigDecimal.ZERO,
          BigDecimal.ZERO);
  static final VatCalculationInput FEBRUARY_VAT =
      new VatCalculationInput(new BigDecimal("6808"), BigDecimal.ZERO, new BigDecimal("68.54"));
  static final ZusCalculationInput FEBRUARY_ZUS_UOP =
      new ZusCalculationInput(true, true, "JDG", false, new BigDecimal("60000"), null);

  private February2026CalculatorFixture() {}
}
