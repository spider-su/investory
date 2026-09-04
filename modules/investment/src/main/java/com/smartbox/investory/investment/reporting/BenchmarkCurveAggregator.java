package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import java.util.ArrayList;
import java.util.List;

/** Aggregates selected account curves in one pass per month. */
final class BenchmarkCurveAggregator {

  private BenchmarkCurveAggregator() {}

  static AggregatedCurves aggregate(List<Benchmark.AccountSeries> series, int monthCount) {
    List<Double> portfolio = new ArrayList<>(monthCount);
    List<Double> benchmark = new ArrayList<>(monthCount);
    for (int month = 0; month < monthCount; month++) {
      double portfolioValue = 0.0;
      double benchmarkValue = 0.0;
      boolean benchmarkPresent = false;
      for (Benchmark.AccountSeries account : series) {
        portfolioValue += account.portfolioCurve().get(month);
        Double value = account.benchmarkCurve().get(month);
        if (value != null) {
          benchmarkValue += value;
          benchmarkPresent = true;
        }
      }
      portfolio.add(round(portfolioValue));
      benchmark.add(benchmarkPresent ? round(benchmarkValue) : null);
    }
    return new AggregatedCurves(portfolio, benchmark);
  }

  record AggregatedCurves(List<Double> portfolio, List<Double> benchmark) {}

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
