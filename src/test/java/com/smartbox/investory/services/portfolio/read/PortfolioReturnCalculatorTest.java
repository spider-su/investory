package com.smartbox.investory.services.portfolio.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PortfolioReturnCalculatorTest {

  @Test
  void annualizesOneYearReturn() {
    ReturnMetric result =
        PortfolioReturnCalculator.annualized(
            ReturnMetric.available(new BigDecimal("0.10")),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31));

    assertEquals(0.10, result.value().doubleValue(), 0.0001);
  }

  @Test
  void annualizesCompoundedMultiYearReturn() {
    ReturnMetric result =
        PortfolioReturnCalculator.annualized(
            ReturnMetric.available(new BigDecimal("0.21")),
            LocalDate.of(2022, 1, 1),
            LocalDate.of(2024, 1, 1));

    assertEquals(Math.sqrt(1.21) - 1.0, result.value().doubleValue(), 0.0001);
  }

  @Test
  void preservesNegativeAndZeroReturns() {
    assertEquals(
        -0.1,
        PortfolioReturnCalculator.annualized(
                ReturnMetric.available(new BigDecimal("-0.19")),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 1, 1))
            .value()
            .doubleValue(),
        0.0001);
    assertEquals(
        0.0,
        PortfolioReturnCalculator.annualized(
                ReturnMetric.available(BigDecimal.ZERO),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1))
            .value()
            .doubleValue(),
        0.0001);
  }

  @Test
  void returnsUnavailableForMissingOrInsufficientHistory() {
    ReturnMetric missing =
        PortfolioReturnCalculator.annualized(
            ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "missing"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2025, 1, 1));
    ReturnMetric sameDay =
        PortfolioReturnCalculator.annualized(
            ReturnMetric.available(BigDecimal.ZERO),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 1));

    assertTrue(missing.status() != ReturnMetric.Status.AVAILABLE);
    assertTrue(sameDay.status() != ReturnMetric.Status.AVAILABLE);
  }
}
