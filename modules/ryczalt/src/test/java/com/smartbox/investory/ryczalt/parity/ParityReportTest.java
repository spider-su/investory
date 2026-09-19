package com.smartbox.investory.ryczalt.parity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ParityReportTest {
  @Test
  void reportsDiagnosticDifferencesAndParity() {
    assertTrue(new ParityReport(java.util.List.of()).isParity());
    assertFalse(
        new ParityReport(
                java.util.List.of(
                    new ParityDifference(
                        "2026-01",
                        "tax",
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        ParityDifference.Classification.ROUNDING_DIFFERENCE,
                        "different scale")))
            .isParity());
  }
}
