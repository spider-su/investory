package com.smartbox.investory.accounting;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrustedCounterpartyTreatmentTest {
  private final TrustedCounterpartyTreatment treatment =
      new TrustedCounterpartyTreatment(
          1,
          1,
          2,
          3,
          "PURCHASE",
          "INVOICE",
          "FUEL",
          "DOMESTIC_PURCHASE",
          new BigDecimal("0.50"),
          new BigDecimal("23.00"),
          "KSEF",
          Instant.EPOCH);

  @Test
  void permitsChangingDocumentSpecificValuesButRequiresEveryTreatmentFactToMatch() {
    assertTrue(
        treatment.matches(
            "PURCHASE",
            "INVOICE",
            "FUEL",
            "DOMESTIC_PURCHASE",
            new BigDecimal("0.500"),
            new BigDecimal("23.0"),
            "KSEF"));
    assertFalse(
        treatment.matches(
            "PURCHASE",
            "INVOICE",
            "SERVICE",
            "DOMESTIC_PURCHASE",
            new BigDecimal("0.50"),
            new BigDecimal("23.00"),
            "KSEF"));
    assertFalse(
        treatment.matches(
            "PURCHASE",
            "INVOICE",
            "FUEL",
            "DOMESTIC_PURCHASE",
            new BigDecimal("1.00"),
            new BigDecimal("23.00"),
            "KSEF"));
    assertFalse(
        treatment.matches(
            "PURCHASE", "INVOICE", "FUEL", "DOMESTIC_PURCHASE", null, new BigDecimal("23"), "KSEF"));
    assertFalse(
        treatment.matches(
            "PURCHASE",
            "INVOICE",
            "FUEL",
            "DOMESTIC_PURCHASE",
            new BigDecimal("0.50"),
            new BigDecimal("23.00"),
            null));
  }
}
