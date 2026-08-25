package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.api.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.InvestmentDashboardApi.DashboardPageView;
import com.smartbox.investory.investment.api.InvestmentDashboardApi.DashboardQuery;
import com.smartbox.investory.investment.api.InvestmentDashboardApi.PerformanceKpiView;
import com.smartbox.investory.investment.reporting.ReturnMetric;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adapter from the dashboard application composition to its public module contract. */
@Service
@RequiredArgsConstructor
public class InvestmentDashboardApplicationService implements InvestmentDashboardApi {
  private final DashboardFacade dashboard;

  @Override
  public DashboardPageView loadDashboard(DashboardQuery query) {
    var page =
        dashboard.loadDashboard(
            new com.smartbox.investory.investment.reporting.dashboard.application.DashboardQuery(
                query.accountIds(),
                query.benchmarkAccountsSubmitted(),
                query.period(),
                query.portfolioId()));
    return new DashboardPageView(
        page.overview(),
        page.performance(),
        page.positions(),
        page.cashFlow(),
        page.risk(),
        page.dataQuality(),
        page.selectedPeriod(),
        page.periods(),
        page.navigation());
  }

  @Override
  public PerformanceKpiView loadPerformanceKpi(Long portfolioId) {
    var performanceKpi = dashboard.loadPerformanceKpi(portfolioId);
    ReturnMetric annualized = performanceKpi.annualizedReturn();
    boolean available = annualized.status() == ReturnMetric.Status.AVAILABLE;
    String display =
        available
            ? DashboardPercentageFormatter.signedPercent(
                    annualized.value().doubleValue() * 100)
                + " p.a."
            : "Unavailable";
    return new PerformanceKpiView(available, display, performanceKpi.startDate());
  }
}
