package com.smartbox.investory.mcp;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;

/** Replaceable in-process/remote boundary for MCP's read-only investment operations. */
public interface InvestmentMcpClient {
  InvestmentDashboardApi.DashboardPageView dashboard(long portfolioId);

  InvestmentDashboardApi.PerformanceKpiView performance(long portfolioId);

  InvestmentDashboardApi.InvestmentResultView yearToDate(long portfolioId);
}
