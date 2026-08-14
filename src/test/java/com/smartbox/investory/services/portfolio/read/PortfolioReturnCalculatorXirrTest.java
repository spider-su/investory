package com.smartbox.investory.services.portfolio.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioReturnCalculatorXirrTest {
  private static final LocalDate START = LocalDate.of(2023, 1, 1);
  private static final LocalDate END = LocalDate.of(2024, 1, 1);

  @Test
  void calculatesNormalOneYearReturn() {
    var result = PortfolioReturnCalculator.xirr(START, bd("100"), END, bd("110"), List.of());
    assertEquals(0.10, result.value().doubleValue(), 0.000001);
  }

  @Test
  void handlesIrregularContribution() {
    var result =
        PortfolioReturnCalculator.xirr(
            START,
            bd("100"),
            END,
            bd("220"),
            List.of(
                new DailyPortfolioValue(LocalDate.of(2023, 7, 1), bd("200"), bd("100"), bd("0"))));
    assertTrue(result.status() == ReturnMetric.Status.AVAILABLE);
    assertTrue(result.value().doubleValue() > -1.0);
  }

  @Test
  void rejectsPortfolioWithNoPositiveTerminalValue() {
    var result = PortfolioReturnCalculator.xirr(START, bd("100"), END, bd("0"), List.of());
    assertNotEquals(ReturnMetric.Status.AVAILABLE, result.status());
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
