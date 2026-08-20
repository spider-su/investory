package com.smartbox.investory.investment.reporting.dashboard.application;

import java.util.List;

public record DashboardQuery(
    List<Long> accountIds, boolean benchmarkAccountsSubmitted, String period, Long portfolioId) {

  public DashboardQuery(List<Long> accountIds, boolean benchmarkAccountsSubmitted, String period) {
    this(accountIds, benchmarkAccountsSubmitted, period, 1L);
  }

  public DashboardQuery {
    accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
    portfolioId = portfolioId == null ? 1L : portfolioId;
  }

  /** An empty submitted selection has the same meaning as the dashboard default: all accounts. */
  public boolean hasExplicitAccountSelection() {
    return benchmarkAccountsSubmitted && !accountIds.isEmpty();
  }
}
