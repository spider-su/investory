package com.smartbox.investory.longterm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LongTermAssetRateConversionTest {
  @Test
  void convertsBetweenCanonicalRatesAndPercentagePoints() {
    assertEquals(
        0,
        new BigDecimal("5.9")
            .compareTo(LongTermAssetRateConversion.rateToPercent(new BigDecimal("0.059"))));
    assertEquals(
        0,
        new BigDecimal("0.04")
            .compareTo(LongTermAssetRateConversion.percentToRate(new BigDecimal("4.0"))));
  }
}
