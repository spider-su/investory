package com.smartbox.investory.investment.api;

import com.smartbox.investory.investment.reporting.dashboard.application.DashboardPageView;
import com.smartbox.investory.investment.reporting.dashboard.application.DashboardQuery;

/** Public dashboard read boundary for presentation adapters. */
public interface InvestmentDashboardApi {
  DashboardPageView loadDashboard(DashboardQuery query);
}
