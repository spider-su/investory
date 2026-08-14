package com.smartbox.investory.application.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PortfolioPeriodMetricsServiceTest {

  private final PortfolioPeriodMetricsService service = new PortfolioPeriodMetricsService();

  @Test
  void compoundsCanonicalMonthlyTwrAndSkipsPreInceptionMonths() {
    assertEquals(12.2, service.compoundAccountReturn(Arrays.asList(null, 10.0, 2.0)), 0.001);
    assertNull(service.compoundAccountReturn(Arrays.asList(null, null)));
  }

  @Test
  void normalizesPositiveWithholdingTaxAndUsesPeriodOpeningCapitalForYield() {
    assertEquals(5_189.0, service.netIncome(4_903.0, 378.0, 664.0));
    assertEquals(10.378, service.incomeYield(5_189.0, 50_000.0, 1_000.0), 0.001);
  }
}
