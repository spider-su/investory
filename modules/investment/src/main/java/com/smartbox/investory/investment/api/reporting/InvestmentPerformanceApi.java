package com.smartbox.investory.investment.api.reporting;

import java.math.BigDecimal;
import java.util.List;

/** Canonical, persistence-free performance read contract for UI and other adapters. */
public interface InvestmentPerformanceApi {
  PerformanceBoardView load(PerformanceBoardQuery query);

  AccountValueView loadAccountValues(Long portfolioId, List<Long> accountIds);

  record PerformanceBoardQuery(
      List<Long> accountIds,
      PerformanceAggregation aggregation,
      PerformanceMetric metric,
      PerformanceStyle style,
      DashboardPeriod period,
      Long portfolioId) {
    public PerformanceBoardQuery {
      accountIds =
          accountIds == null || accountIds.isEmpty()
              ? null
              : com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(accountIds);
      aggregation = aggregation == null ? PerformanceAggregation.MONTHLY : aggregation;
      metric = metric == null ? PerformanceMetric.RETURN : metric;
      style = style == null ? PerformanceStyle.LINE : style;
      if (portfolioId == null || portfolioId <= 0) {
        throw new IllegalArgumentException("portfolioId must be positive");
      }
    }
  }

  record PerformanceBoardView(
      boolean available,
      List<String> labels,
      List<PerformanceSeries> series,
      List<BigDecimal> benchmarkValues,
      List<BigDecimal> excessValues,
      PerformanceKpiView kpis,
      List<PerformanceAccount> accounts) {}

  record PerformanceSeries(Long accountId, String label, List<BigDecimal> values) {}

  record PerformanceAccount(Long id, String name, boolean selected) {}

  record PerformanceKpiView(
      BigDecimal portfolioReturn,
      BigDecimal benchmarkReturn,
      BigDecimal excessReturn,
      BigDecimal portfolioProfitLoss,
      String bestPeriod,
      BigDecimal bestValue,
      String worstPeriod,
      BigDecimal worstValue) {}

  record AccountValueView(List<AccountValueYear> years) {
    public AccountValueView {
      years = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(years);
    }
  }

  record AccountValueYear(
      int year,
      List<String> labels,
      List<AccountValueSeries> accountSeries,
      List<BigDecimal> totalProfitValues,
      List<BigDecimal> totalProfitPctValues) {
    public AccountValueYear {
      labels = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(labels);
      accountSeries =
          com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(accountSeries);
      totalProfitValues =
          com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(
              totalProfitValues);
      totalProfitPctValues =
          com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(
              totalProfitPctValues);
    }
  }

  record AccountValueSeries(
      Long id, String name, List<BigDecimal> profitValues, List<BigDecimal> profitPctValues) {
    public AccountValueSeries {
      profitValues =
          com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(profitValues);
      profitPctValues =
          com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(profitPctValues);
    }
  }
}
