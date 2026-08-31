package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Period Metrics Service")
class PortfolioPeriodMetricsServiceTest {

  private final PortfolioPeriodMetricsService service = new PortfolioPeriodMetricsService();

  @DisplayName("compounds Canonical Monthly Twr And Skips Pre Inception Months")
  @Test
  void compoundsCanonicalMonthlyTwrAndSkipsPreInceptionMonths() {
    assertEquals(12.2, service.compoundAccountReturn(Arrays.asList(null, 10.0, 2.0)), 0.001);
    assertNull(service.compoundAccountReturn(Arrays.asList(null, null)));
  }

  @DisplayName("normalizes Positive Withholding Tax And Uses Period Opening Capital For Yield")
  @Test
  void normalizesPositiveWithholdingTaxAndUsesPeriodOpeningCapitalForYield() {
    assertEquals(5_189.0, service.netIncome(4_903.0, 378.0, 664.0));
    assertEquals(10.378, service.incomeYield(5_189.0, 50_000.0, 1_000.0), 0.001);
  }
}
