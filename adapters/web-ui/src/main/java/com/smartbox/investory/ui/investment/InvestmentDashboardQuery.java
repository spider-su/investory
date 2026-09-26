package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import java.util.List;

/** Request data needed by the dashboard page. */
public record InvestmentDashboardQuery(
    List<Long> accountIds,
    boolean benchmarkAccountsSubmitted,
    DashboardPeriod period,
    Long portfolioId) {}
