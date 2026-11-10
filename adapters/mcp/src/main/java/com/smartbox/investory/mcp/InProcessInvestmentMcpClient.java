package com.smartbox.investory.mcp;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class InProcessInvestmentMcpClient implements InvestmentMcpClient {
  private final InvestmentDashboardApi dashboard;

  public InProcessInvestmentMcpClient(InvestmentDashboardApi dashboard) {
    this.dashboard = dashboard;
  }

  @Override
  public InvestmentDashboardApi.DashboardPageView dashboard(long portfolioId) {
    return dashboard.loadDashboard(
        new InvestmentDashboardApi.DashboardQuery(List.of(), false, null, portfolioId));
  }

  @Override
  public InvestmentDashboardApi.PerformanceKpiView performance(long portfolioId) {
    return dashboard.loadPerformanceKpi(portfolioId);
  }

  @Override
  public InvestmentDashboardApi.InvestmentResultView yearToDate(long portfolioId) {
    return dashboard.investmentResultYtd(portfolioId);
  }
}
