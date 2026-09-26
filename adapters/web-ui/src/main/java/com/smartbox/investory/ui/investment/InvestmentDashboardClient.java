package com.smartbox.investory.ui.investment;

/** UI boundary for the in-process investment dashboard endpoint. */
public interface InvestmentDashboardClient {
  InvestmentDashboardPageView loadDashboard(InvestmentDashboardQuery query);

  InvestmentPerformanceKpi loadPerformanceKpi(Long portfolioId);

  InvestmentResult investmentResultYtd(Long portfolioId);
}
