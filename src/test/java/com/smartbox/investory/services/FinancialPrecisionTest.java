package com.smartbox.investory.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FinancialPrecisionTest {

  @Test
  void moneyUsesHalfUpForPositiveAndNegativeHalfCents() {
    assertEquals(new BigDecimal("100.00"), FinancialPrecision.money(new BigDecimal("100.004")));
    assertEquals(new BigDecimal("100.01"), FinancialPrecision.money(new BigDecimal("100.005")));
    assertEquals(new BigDecimal("-100.01"), FinancialPrecision.money(new BigDecimal("-100.005")));
  }

  @Test
  void moneyRoundsOnlyAfterAggregation() {
    BigDecimal aggregate = new BigDecimal("100.004").add(new BigDecimal("0.004"));

    assertEquals(new BigDecimal("100.01"), FinancialPrecision.money(aggregate));
  }

  @Test
  void ratesAndPercentagesUseDifferentScales() {
    assertEquals(new BigDecimal("1.234568"), FinancialPrecision.rate(new BigDecimal("1.2345678")));
    assertEquals(new BigDecimal("12.35"), FinancialPrecision.percentage(new BigDecimal("12.345")));
    assertEquals(
        new BigDecimal("12.35"), FinancialPrecision.percentageRatio(new BigDecimal("0.12345")));
    assertEquals(
        new BigDecimal("12.3457"), FinancialPrecision.returnPercentage(new BigDecimal("12.34567")));
  }

  @Test
  void quantitiesAreNotPassedThroughMoneyPolicy() {
    BigDecimal quantity = new BigDecimal("10.123456");

    assertEquals(quantity, quantity.stripTrailingZeros().add(BigDecimal.ZERO));
    assertEquals(new BigDecimal("10.12"), FinancialPrecision.money(quantity));
  }

  @Test
  void ratioPreservesCalculationPrecisionUntilDisplay() {
    BigDecimal ratio = FinancialPrecision.ratio(new BigDecimal("1"), new BigDecimal("3"));

    assertEquals(new BigDecimal("0.33333333"), ratio);
    assertEquals(new BigDecimal("33.33"), FinancialPrecision.percentageRatio(ratio));
  }
}
