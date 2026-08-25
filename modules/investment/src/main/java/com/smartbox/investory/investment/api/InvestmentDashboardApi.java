package com.smartbox.investory.investment.api;

import java.util.List;

/** Public dashboard read boundary for presentation adapters. */
public interface InvestmentDashboardApi {
  DashboardPageView loadDashboard(DashboardQuery query);

  /** Canonical annualized total return used by every UI that shows the investment KPI. */
  PerformanceKpiView loadPerformanceKpi(Long portfolioId);

  record PerformanceKpiView(boolean available, String annualizedReturnDisplay, String kpiStartDate) {
    public PerformanceKpiView {
      annualizedReturnDisplay =
          annualizedReturnDisplay == null || annualizedReturnDisplay.isBlank()
              ? "Unavailable"
              : annualizedReturnDisplay;
    }
  }

  record DashboardQuery(
      List<Long> accountIds, boolean benchmarkAccountsSubmitted, String period, Long portfolioId) {
    public DashboardQuery(
        List<Long> accountIds, boolean benchmarkAccountsSubmitted, String period) {
      this(accountIds, benchmarkAccountsSubmitted, period, 1L);
    }

    public DashboardQuery {
      accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
      portfolioId = portfolioId == null ? 1L : portfolioId;
    }
  }

  /** Public dashboard page contract; concrete view objects remain internal to Investment. */
  record DashboardPageView(
      Object overview,
      Object performance,
      Object positions,
      Object cashFlow,
      Object risk,
      Object dataQuality,
      Object selectedPeriod,
      @SuppressWarnings("rawtypes") List periods,
      Object navigation) {
    public DashboardPageView {
      periods = periods == null ? List.of() : List.copyOf(periods);
    }
  }
}
