package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.model.CashFlowView;
import com.smartbox.investory.investment.api.reporting.model.DashboardNavigationView;
import com.smartbox.investory.investment.api.reporting.model.DataQualityView;
import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.investment.api.reporting.model.PerformanceView;
import com.smartbox.investory.investment.api.reporting.model.PositionsView;
import com.smartbox.investory.investment.api.reporting.model.RiskView;
import java.util.List;

/** Page-facing dashboard view owned by the Web UI boundary. */
public record InvestmentDashboardPageView(
    OverviewView overview,
    PerformanceView performance,
    PositionsView positions,
    CashFlowView cashFlow,
    RiskView risk,
    DataQualityView dataQuality,
    DashboardPeriod selectedPeriod,
    List<DashboardPeriod> periods,
    DashboardNavigationView navigation) {}
