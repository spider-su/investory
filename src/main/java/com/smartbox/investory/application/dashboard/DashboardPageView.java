package com.smartbox.investory.application.dashboard;

import com.smartbox.investory.services.dashboard.DashboardPeriod;
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
