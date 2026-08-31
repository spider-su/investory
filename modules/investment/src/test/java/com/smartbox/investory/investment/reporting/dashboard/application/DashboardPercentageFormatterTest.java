package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DashboardPercentageFormatterTest {
  @Test
  void formatsNormalPercentagesToOneDecimalWithLocale() {
    assertEquals("16.9%", DashboardPercentageFormatter.percent(16.86));
    assertEquals("4.2%", DashboardPercentageFormatter.percent(4.24));
    assertEquals("4.3%", DashboardPercentageFormatter.percent(4.25));
    assertEquals("0.0%", DashboardPercentageFormatter.percent(0.0));
    assertEquals("-3.7%", DashboardPercentageFormatter.percent(-3.74));
  }

  @Test
  void formatsSignedPercentagesAndPercentagePoints() {
    assertEquals("+8.4%", DashboardPercentageFormatter.signedPercent(8.42));
    assertEquals("+0.8 pp", DashboardPercentageFormatter.percentagePoints(0.84));
    assertEquals("-1.3 pp", DashboardPercentageFormatter.percentagePoints(-1.26));
  }
}
