package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.api.InvestmentDashboardApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adapter from the dashboard application composition to its public module contract. */
@Service
@RequiredArgsConstructor
public class InvestmentDashboardApplicationService implements InvestmentDashboardApi {
  private final DashboardFacade dashboard;

  @Override
  public DashboardPageView loadDashboard(DashboardQuery query) {
    return dashboard.loadDashboard(query);
  }
}
