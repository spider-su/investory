package com.smartbox.investory.investment.api.reporting.model;

import java.math.BigDecimal;

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
    String kpiStartDate,
    ReturnMetric expectedAnnualReturn,
    ReturnMetric historicalAnnualizedReturn,
    String historyContext) {
  public PerformanceSummary(
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
    this(
        portfolioReturnPct,
        benchmarkReturnPct,
        excessReturnPct,
        portfolioPl,
        currentDrawdownPct,
        maxDrawdownPct,
        bestPeriod,
        bestPeriodPl,
        worstPeriod,
        worstPeriodPl,
        timeWeightedReturn,
        moneyWeightedReturn,
        attribution,
        kpiReturn,
        annualizedReturn,
        kpiStartDate,
        annualizedReturn,
        annualizedReturn,
        null);
  }

  public String formatPercent(Double value) {
    return DashboardPercentageFormatter.percent(value);
  }

  /**
   * @deprecated display formatting belongs to the web adapter; retained for old templates.
   */
  @Deprecated(forRemoval = false)
  public String formatPercent(BigDecimal value) {
    return DashboardPercentageFormatter.percent(value);
  }

  public String formatSignedPercent(Double value) {
    return DashboardPercentageFormatter.signedPercent(value);
  }

  public String formatPercentagePoints(Double value) {
    return DashboardPercentageFormatter.percentagePoints(value);
  }
}
