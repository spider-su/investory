package com.smartbox.investory.application.dashboard;

import com.smartbox.investory.investment.reporting.PerformanceAttribution;
import com.smartbox.investory.investment.reporting.ReturnMetric;

public record PerformanceSummary(
    double portfolioReturnPct,
    Double benchmarkReturnPct,
    Double excessReturnPct,
    double portfolioPl,
    double currentDrawdownPct,
    double maxDrawdownPct,
    String bestPeriod,
    double bestPeriodPl,
    String worstPeriod,
    double worstPeriodPl,
    ReturnMetric timeWeightedReturn,
    ReturnMetric moneyWeightedReturn,
    PerformanceAttribution attribution,
    ReturnMetric kpiReturn,
    ReturnMetric annualizedReturn,
    String kpiStartDate) {
  public String formatPercent(Double value) {
    return DashboardPercentageFormatter.percent(value);
  }

  public String formatSignedPercent(Double value) {
    return DashboardPercentageFormatter.signedPercent(value);
  }

  public String formatPercentagePoints(Double value) {
    return DashboardPercentageFormatter.percentagePoints(value);
  }
}
