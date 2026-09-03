package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.DashboardPercentageFormatter;
import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQuery;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Adapter from the dashboard application composition to its public module contract. */
@Service
@Primary
@RequiredArgsConstructor
public class InvestmentDashboardApplicationService implements InvestmentDashboardApi {
  private final InvestmentDashboardFacade dashboard;
  private final PortfolioPerformanceQuery performance;
  private final PortfolioContextReader portfolios;

  @Override
  public InvestmentDashboardApi.DashboardPageView loadDashboard(
      InvestmentDashboardApi.DashboardQuery query) {
    requirePortfolio(query.portfolioId());
    return dashboard.loadDashboard(query);
  }

  @Override
  public InvestmentDashboardApi.PerformanceKpiView loadPerformanceKpi(Long portfolioId) {
    requirePortfolio(portfolioId);
    var performanceKpi = dashboard.loadPerformanceKpi(portfolioId);
    ReturnMetric annualized = performanceKpi.annualizedReturn();
    boolean available = annualized.status() == ReturnMetric.Status.AVAILABLE;
    String display =
        available
            ? DashboardPercentageFormatter.signedPercent(annualized.value().doubleValue() * 100)
            : "Unavailable";
    return new InvestmentDashboardApi.PerformanceKpiView(
        available, available ? annualized.value() : null, display, performanceKpi.startDate());
  }

  @Override
  public InvestmentDashboardApi.InvestmentResultView investmentResult(Long portfolioId) {
    requirePortfolio(portfolioId);
    var result = performance.portfolioResult(portfolioId);
    if (result.baseCurrency() == null) {
      return InvestmentDashboardApi.InvestmentResultView.unavailable(null);
    }
    return new InvestmentDashboardApi.InvestmentResultView(
        true, result.investmentResult(), result.baseCurrency());
  }

  private void requirePortfolio(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new InvestmentDashboardApi.InvalidPortfolioRequest("portfolioId must be positive");
    }
    if (portfolios.findById(portfolioId).isEmpty()) {
      throw new InvestmentDashboardApi.PortfolioNotFoundException(portfolioId);
    }
  }
}
