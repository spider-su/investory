package com.smartbox.investory.ryczalt.calculation;

import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationInput;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationInput;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationInput;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Small owned Stage 2 fixture adapted from the existing HappyInvestor accounting facts.
 *
 * <p>Source provenance: {@code test-support/.../happyinvestor-accounting-2026.json} and the Jan-Aug
 * branch rows in {@code V01.011__accounting_poc_test_data.sql}. The new module copies only
 * normalized values and has no dependency on accounting or test-support.
 */
final class HappyInvestorStage2Fixture {
  static final RyczaltCalculationInput FEBRUARY_RYCZALT =
      new RyczaltCalculationInput(
          Map.of(new BigDecimal("0.12"), new BigDecimal("29600")),
          BigDecimal.ZERO,
          BigDecimal.ZERO);
  static final VatCalculationInput FEBRUARY_VAT =
      new VatCalculationInput(new BigDecimal("6808"), BigDecimal.ZERO, new BigDecimal("68.54"));
  static final ZusCalculationInput FEBRUARY_ZUS_UOP =
      new ZusCalculationInput(true, true, "JDG", false, new BigDecimal("60000"), null);

  private HappyInvestorStage2Fixture() {}
}
