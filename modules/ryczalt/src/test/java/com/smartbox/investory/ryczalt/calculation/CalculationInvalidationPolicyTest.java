package com.smartbox.investory.ryczalt.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.ryczalt.persistence.CalculationType;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class CalculationInvalidationPolicyTest {
  @Test
  void mapsChangesOnlyToDependentCalculations() {
    assertEquals(
        EnumSet.of(CalculationType.RYCZALT, CalculationType.VAT),
        CalculationInvalidationPolicy.affectedBy(InputChange.INCOME_INVOICE_CHANGED));
    assertEquals(
        EnumSet.of(CalculationType.VAT),
        CalculationInvalidationPolicy.affectedBy(InputChange.COST_INVOICE_CHANGED));
    assertEquals(
        EnumSet.of(CalculationType.ZUS, CalculationType.RYCZALT),
        CalculationInvalidationPolicy.affectedBy(InputChange.ZUS_INPUT_CHANGED));
    assertEquals(
        EnumSet.of(CalculationType.RYCZALT),
        CalculationInvalidationPolicy.affectedBy(InputChange.RYCZALT_DEDUCTIONS_CHANGED));
    assertEquals(
        EnumSet.of(CalculationType.VAT),
        CalculationInvalidationPolicy.affectedBy(InputChange.VAT_ADJUSTMENT_CHANGED));
    assertEquals(
        EnumSet.noneOf(CalculationType.class),
        CalculationInvalidationPolicy.affectedBy(InputChange.TRANSACTION_CHANGED));
  }
}
