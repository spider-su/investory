package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountMonthlyPerformanceEntity;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Calculates canonical linked monthly portfolio returns from an indexed reporting input. */
final class BenchmarkMonthlyReturnCalculator {

  private BenchmarkMonthlyReturnCalculator() {}

  static List<Double> portfolioReturnCurve(BenchmarkMonthlyIndex index) {
    List<Double> curve = new ArrayList<>();
    double factor = 1.0;
    boolean started = false;
    boolean complete = true;
    for (String label : index.labels()) {
      List<AccountMonthlyPerformanceEntity> monthRows =
          index.rowsByMonth().getOrDefault(YearMonth.parse(label), List.of());
      double capital = monthRows.stream().mapToDouble(row -> nz(row.getStartEquity())).sum();
      if (monthRows.isEmpty()) {
        if (started) complete = false;
        curve.add(null);
      } else if (monthRows.stream().anyMatch(row -> row.getReturnPct() == null)) {
        if (capital != 0.0) started = true;
        if (started) complete = false;
        curve.add(null);
      } else if (capital == 0.0 || !complete) {
        curve.add(null);
      } else {
        started = true;
        double monthReturn =
            monthRows.stream()
                    .mapToDouble(row -> nz(row.getStartEquity()) * nz(row.getReturnPct()))
                    .sum()
                / capital;
        factor = monthReturn <= -1.0 ? 0.0 : factor * (1.0 + monthReturn);
        curve.add(round((factor - 1.0) * 100.0));
      }
    }
    return curve;
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static double nz(BigDecimal value) {
    return value == null ? 0.0 : value.doubleValue();
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
