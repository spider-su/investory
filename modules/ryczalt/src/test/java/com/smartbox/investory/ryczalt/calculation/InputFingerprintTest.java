package com.smartbox.investory.ryczalt.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationInput;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InputFingerprintTest {
  @Test
  void isIndependentOfMapInsertionOrder() {
    Map<BigDecimal, BigDecimal> first = new LinkedHashMap<>();
    first.put(new BigDecimal("0.12"), new BigDecimal("100.00"));
    first.put(new BigDecimal("0.03"), new BigDecimal("50.00"));
    Map<BigDecimal, BigDecimal> second = new LinkedHashMap<>();
    second.put(new BigDecimal("0.03"), new BigDecimal("50.00"));
    second.put(new BigDecimal("0.12"), new BigDecimal("100.00"));
    assertEquals(
        InputFingerprint.ryczalt(
            new RyczaltCalculationInput(first, BigDecimal.ZERO, BigDecimal.ZERO), "v1"),
        InputFingerprint.ryczalt(
            new RyczaltCalculationInput(second, BigDecimal.ZERO, BigDecimal.ZERO), "v1"));
  }

  @Test
  void changesWhenRelevantInputChanges() {
    RyczaltCalculationInput first =
        new RyczaltCalculationInput(
            Map.of(new BigDecimal("0.12"), new BigDecimal("100")),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    RyczaltCalculationInput second =
        new RyczaltCalculationInput(
            Map.of(new BigDecimal("0.12"), new BigDecimal("101")),
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    assertNotEquals(InputFingerprint.ryczalt(first, "v1"), InputFingerprint.ryczalt(second, "v1"));
  }
}
