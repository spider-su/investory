package com.smartbox.investory.ryczalt.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RoundingPolicyTest {
  @Test
  void keepsTwoDecimalSemanticBoundariesHalfUp() {
    assertEquals(new BigDecimal("100.50"), RoundingPolicy.roundFxAmount(new BigDecimal("100.495")));
    assertEquals(
        new BigDecimal("100.50"), RoundingPolicy.roundZusContribution(new BigDecimal("100.495")));
    assertEquals(
        new BigDecimal("100.50"), RoundingPolicy.roundHealthDeduction(new BigDecimal("100.495")));
    assertEquals(
        new BigDecimal("100.50"),
        RoundingPolicy.roundDeductionAllocation(new BigDecimal("100.495")));
  }

  @Test
  void keepsWholePlnBoundariesHalfUp() {
    assertEquals(
        new BigDecimal("100"), RoundingPolicy.roundRyczaltTaxableBase(new BigDecimal("100.49")));
    assertEquals(new BigDecimal("101"), RoundingPolicy.roundRyczaltTax(new BigDecimal("100.50")));
    assertEquals(
        new BigDecimal("101"), RoundingPolicy.roundVatSettlementAmount(new BigDecimal("100.51")));
  }
}
