package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.web.InvestmentDashboardRestController;
import org.springframework.stereotype.Component;

@Component
public class InProcessInvestmentDashboardClient implements InvestmentDashboardClient {
  private final InvestmentDashboardRestController rest;

  public InProcessInvestmentDashboardClient(InvestmentDashboardRestController rest) {
    this.rest = rest;
  }

  @Override
  public InvestmentDashboardPageView loadDashboard(InvestmentDashboardQuery query) {
    var page =
        rest.dashboard(
            query.portfolioId(),
            new InvestmentDashboardRestController.DashboardRequest(
                query.accountIds(), query.benchmarkAccountsSubmitted(), query.period()));
    return new InvestmentDashboardPageView(
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
  public InvestmentPerformanceKpi loadPerformanceKpi(Long portfolioId) {
    var value = rest.performanceKpi(portfolioId);
    return new InvestmentPerformanceKpi(
        value.available(),
        value.totalReturn(),
        value.totalReturnDisplay(),
        value.kpiStartDate(),
        value.historicalAnnualizedReturn(),
        value.historicalAnnualizedReturnDisplay(),
        value.expectedAnnualReturn(),
        value.expectedAnnualReturnDisplay(),
        value.historyYears(),
        value.historyContext());
  }

  @Override
  public InvestmentResult investmentResultYtd(Long portfolioId) {
    var value = rest.investmentResultYtd(portfolioId);
    return new InvestmentResult(value.available(), value.amount(), value.currency());
  }
}
