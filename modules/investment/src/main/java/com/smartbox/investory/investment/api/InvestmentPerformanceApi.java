package com.smartbox.investory.investment.api;

import java.util.List;

/** Canonical, persistence-free performance read contract for UI and other adapters. */
public interface InvestmentPerformanceApi {
  PerformanceBoardView load(PerformanceBoardQuery query);

  record PerformanceBoardQuery(
      List<Long> accountIds, String aggregation, String metric, String style) {
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
      PerformanceKpiView kpis,
      List<PerformanceAccount> accounts) {}

  record PerformanceSeries(String label, List<Double> values) {}

  record PerformanceAccount(Long id, String name, boolean selected) {}

  record PerformanceKpiView(
      Double portfolioReturn,
      Double benchmarkReturn,
      Double excessReturn,
      String bestPeriod,
      Double bestValue,
      String worstPeriod,
      Double worstValue) {}
}
