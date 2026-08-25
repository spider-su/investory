package com.smartbox.investory.investment.api;

import java.util.List;

/** Canonical, persistence-free performance read contract for UI and other adapters. */
public interface InvestmentPerformanceApi {
  PerformanceBoardView load(PerformanceBoardQuery query);

  AccountValueView loadAccountValues(List<Long> accountIds);

  record PerformanceBoardQuery(
      List<Long> accountIds, String aggregation, String metric, String style, String period) {
    public PerformanceBoardQuery(
        List<Long> accountIds, String aggregation, String metric, String style) {
      this(accountIds, aggregation, metric, style, null);
    }

    public PerformanceBoardQuery {
      accountIds = accountIds == null ? null : List.copyOf(accountIds);
      aggregation = aggregation == null ? "monthly" : aggregation;
      metric = metric == null ? "return" : metric;
      style = style == null ? "line" : style;
    }
  }

  record PerformanceBoardView(
      boolean available,
      List<String> labels,
      List<PerformanceSeries> series,
      List<Double> benchmarkValues,
      List<Double> excessValues,
      PerformanceKpiView kpis,
      List<PerformanceAccount> accounts) {}

  record PerformanceSeries(String label, List<Double> values) {}

  record PerformanceAccount(Long id, String name, boolean selected) {}

  record PerformanceKpiView(
      Double portfolioReturn,
      Double benchmarkReturn,
      Double excessReturn,
      Double portfolioProfitLoss,
      String bestPeriod,
      Double bestValue,
      String worstPeriod,
      Double worstValue) {}

  record AccountValueView(List<AccountValueYear> years) {
    public AccountValueView {
      years = years == null ? List.of() : List.copyOf(years);
    }
  }

  record AccountValueYear(
      int year,
      List<String> labels,
      List<AccountValueSeries> accountSeries,
      List<Double> totalProfitValues,
      List<Double> totalProfitPctValues) {
    public AccountValueYear {
      labels = labels == null ? List.of() : List.copyOf(labels);
      accountSeries = accountSeries == null ? List.of() : List.copyOf(accountSeries);
      totalProfitValues = totalProfitValues == null ? List.of() : List.copyOf(totalProfitValues);
      totalProfitPctValues =
          totalProfitPctValues == null ? List.of() : List.copyOf(totalProfitPctValues);
    }
  }

  record AccountValueSeries(
      Long id, String name, List<Double> profitValues, List<Double> profitPctValues) {
    public AccountValueSeries {
      profitValues = profitValues == null ? List.of() : List.copyOf(profitValues);
      profitPctValues = profitPctValues == null ? List.of() : List.copyOf(profitPctValues);
    }
  }
}
