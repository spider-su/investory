package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessInvestmentDashboardClient implements InvestmentDashboardClient {
  private final InvestmentDashboardApi investmentDashboardApi;

  public InProcessInvestmentDashboardClient(
      @Qualifier("investmentDashboardApplicationService")
          InvestmentDashboardApi investmentDashboardApi) {
    this.investmentDashboardApi = investmentDashboardApi;
  }

  @Override
  public DashboardPageView loadDashboard(DashboardQuery query) {
    return investmentDashboardApi.loadDashboard(query);
  }

  @Override
  public PerformanceKpiView loadPerformanceKpi(Long portfolioId) {
    return investmentDashboardApi.loadPerformanceKpi(portfolioId);
  }

  @Override
  public InvestmentResultView investmentResult(Long portfolioId) {
    return investmentDashboardApi.investmentResult(portfolioId);
  }
}
