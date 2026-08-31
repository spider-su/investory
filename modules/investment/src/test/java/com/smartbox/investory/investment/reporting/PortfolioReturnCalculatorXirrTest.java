package com.smartbox.investory.investment.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Return Calculator Xirr")
class PortfolioReturnCalculatorXirrTest {
  private static final LocalDate START = LocalDate.of(2023, 1, 1);
  private static final LocalDate END = LocalDate.of(2024, 1, 1);

  @DisplayName("calculates Normal One Year Return")
  @Test
  void calculatesNormalOneYearReturn() {
    var result = PortfolioReturnCalculator.xirr(START, bd("100"), END, bd("110"), List.of());
    assertEquals(0.10, result.value().doubleValue(), 0.000001);
  }

  @DisplayName("handles Irregular Contribution")
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

  @DisplayName("rejects Portfolio With No Positive Terminal Value")
  @Test
  void rejectsPortfolioWithNoPositiveTerminalValue() {
    var result = PortfolioReturnCalculator.xirr(START, bd("100"), END, bd("0"), List.of());
    assertNotEquals(ReturnMetric.Status.AVAILABLE, result.status());
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
