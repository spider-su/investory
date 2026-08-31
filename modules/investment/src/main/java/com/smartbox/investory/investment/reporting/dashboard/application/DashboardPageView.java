package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriod;
import java.util.List;

public record DashboardPageView(
    OverviewView overview,
    PerformanceView performance,
    PositionsView positions,
    CashFlowView cashFlow,
    RiskView risk,
    DataQualityView dataQuality,
    DashboardPeriod selectedPeriod,
    List<DashboardPeriod> periods,
    DashboardNavigationView navigation) {

  public DashboardPageView {
    periods = List.copyOf(periods);
  }
}
