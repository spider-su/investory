package com.smartbox.investory.ryczalt.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationResult;
import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculator;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationResult;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculator;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationResult;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculator;
import com.smartbox.investory.ryczalt.calculation.zus.ZusRules2026;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HappyInvestorCalculationTest {
  @Test
  void reproducesNormalizedFebruaryReferenceValues() {
    RyczaltCalculationResult ryczalt =
        new RyczaltCalculator().calculate(February2026CalculatorFixture.FEBRUARY_RYCZALT);
    VatCalculationResult vat =
        new VatCalculator().calculate(February2026CalculatorFixture.FEBRUARY_VAT);
    ZusCalculationResult zus =
        new ZusCalculator().calculate(February2026CalculatorFixture.FEBRUARY_ZUS_UOP);

    assertAmount("3552", ryczalt.calculatedTax());
    assertAmount("6739", vat.calculatedVat());
    assertAmount("498.35", zus.health());
    assertAmount("0", zus.social());
    assertEquals(ZusRules2026.HealthBand.LOW, zus.healthBand());
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
