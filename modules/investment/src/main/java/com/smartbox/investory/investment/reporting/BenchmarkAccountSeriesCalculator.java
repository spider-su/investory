package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds account-level monthly curves from the indexed monthly input. */
final class BenchmarkAccountSeriesCalculator {

  private static final double ACTIVE_ACCOUNT_MIN_VALUE = 50.0;

  private BenchmarkAccountSeriesCalculator() {}

  static Benchmark.AccountSeries calculate(
      Long accountId, BenchmarkMonthlyIndex index, java.util.NavigableMap<String, Double> closes) {
    Map<YearMonth, AccountMonthlyPerformanceEntity> rows =
        index.rowsByAccount().getOrDefault(accountId, Map.of());
    String startLabel =
        index.labels().stream()
            .filter(
                label -> {
                  AccountMonthlyPerformanceEntity row = rows.get(YearMonth.parse(label));
                  return row != null
                      && Math.abs(nz(row.getStartEquity())) > ACTIVE_ACCOUNT_MIN_VALUE;
                })
            .findFirst()
            .orElse(null);
    List<Double> portfolioCurve = new ArrayList<>();
    List<Double> benchmarkCurve = new ArrayList<>();
    List<Double> returnCapitalCurve = new ArrayList<>();
    List<Double> returnContributionCurve = new ArrayList<>();
    List<Double> returnPctCurve = new ArrayList<>();
    if (startLabel == null) {
      return new Benchmark.AccountSeries(
          accountId,
          0.0,
          0.0,
          0.0,
          portfolioCurve,
          benchmarkCurve,
          returnCapitalCurve,
          returnContributionCurve,
          returnPctCurve);
    }
    double basePortfolioValue = nz(rows.get(YearMonth.parse(startLabel)).getStartEquity());
    Double baseClose =
        BenchmarkService.benchmarkBaseClose(
            index.labels().subList(index.labels().indexOf(startLabel), index.labels().size()),
            closes);
    boolean benchmarkAvailable = baseClose != null && baseClose != 0.0;
    double cumulativeProfit = 0.0;
    boolean started = false;
    for (String label : index.labels()) {
      if (!started && !label.equals(startLabel)) {
        portfolioCurve.add(0.0);
        benchmarkCurve.add(0.0);
        returnCapitalCurve.add(null);
        returnContributionCurve.add(null);
        returnPctCurve.add(null);
        continue;
      }
      started = true;
      AccountMonthlyPerformanceEntity row = rows.get(YearMonth.parse(label));
      if (row != null) cumulativeProfit += nz(row.getProfit());
      double openingCapital = nz(row == null ? null : row.getStartEquity());
      Double monthlyReturn = row == null ? null : toDouble(row.getReturnPct());
      if (openingCapital == 0.0 || monthlyReturn == null) {
        returnCapitalCurve.add(null);
        returnContributionCurve.add(null);
        returnPctCurve.add(null);
      } else {
        returnCapitalCurve.add(round(openingCapital));
        returnContributionCurve.add(round(openingCapital * monthlyReturn));
        returnPctCurve.add(round(monthlyReturn * 100.0));
      }
      Double close = closes.get(label);
      Double benchmarkProfit =
          benchmarkAvailable && close != null && close != 0.0
              ? basePortfolioValue * (close / baseClose - 1.0)
              : null;
      portfolioCurve.add(round(cumulativeProfit));
      benchmarkCurve.add(benchmarkProfit == null ? null : round(benchmarkProfit));
    }
    return new Benchmark.AccountSeries(
        accountId,
        round(basePortfolioValue),
        portfolioCurve.getLast(),
        benchmarkCurve.isEmpty() || benchmarkCurve.getLast() == null
            ? 0.0
            : benchmarkCurve.getLast(),
        portfolioCurve,
        benchmarkCurve,
        returnCapitalCurve,
        returnContributionCurve,
        returnPctCurve);
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static double nz(BigDecimal value) {
    return value == null ? 0.0 : value.doubleValue();
  }

  private static Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
