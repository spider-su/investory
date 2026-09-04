package com.smartbox.investory.investment.api.reporting;

import com.smartbox.investory.investment.api.reporting.model.CashFlowView;
import com.smartbox.investory.investment.api.reporting.model.DashboardNavigationView;
import com.smartbox.investory.investment.api.reporting.model.DataQualityView;
import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.investment.api.reporting.model.PerformanceView;
import com.smartbox.investory.investment.api.reporting.model.PositionsView;
import com.smartbox.investory.investment.api.reporting.model.RiskView;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/** Public dashboard read boundary for presentation adapters. */
public interface InvestmentDashboardApi {
  /**
   * Loads the system-wide dashboard with portfolio-scoped canonical performance and structure
   * sections selected by {@link DashboardQuery#portfolioId()}. The portfolio id is required so
   * those sections cannot silently fall back to an arbitrary portfolio.
   */
  DashboardPageView loadDashboard(DashboardQuery query);

  /** Canonical annualized total return used by every UI that shows the investment KPI. */
  PerformanceKpiView loadPerformanceKpi(Long portfolioId);

  /** Total investment result in the portfolio base currency. */
  InvestmentResultView investmentResult(Long portfolioId);

  record PerformanceKpiView(
      boolean available,
      BigDecimal annualizedReturn,
      String annualizedReturnDisplay,
      String kpiStartDate,
      BigDecimal annualizedIncome) {
    public PerformanceKpiView(
        boolean available, String annualizedReturnDisplay, String kpiStartDate) {
      this(available, null, annualizedReturnDisplay, kpiStartDate, null);
    }

    public PerformanceKpiView(
        boolean available,
        BigDecimal annualizedReturn,
        String annualizedReturnDisplay,
        String kpiStartDate) {
      this(available, annualizedReturn, annualizedReturnDisplay, kpiStartDate, null);
    }

    public PerformanceKpiView {
      annualizedReturnDisplay =
          annualizedReturnDisplay == null || annualizedReturnDisplay.isBlank()
              ? "Unavailable"
              : annualizedReturnDisplay;
    }
  }

  record InvestmentResultView(boolean available, BigDecimal amount, CurrencyType currency) {
    public static InvestmentResultView unavailable(CurrencyType currency) {
      return new InvestmentResultView(false, null, currency);
    }
  }

  record DashboardQuery(
      List<Long> accountIds,
      boolean benchmarkAccountsSubmitted,
      DashboardPeriod period,
      Long portfolioId) {
    public DashboardQuery {
      accountIds =
          com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(accountIds);
      if (portfolioId == null || portfolioId <= 0) {
        throw new InvalidPortfolioRequest("portfolioId must be positive");
      }
      period = period == null ? DashboardPeriod.YEAR_TO_DATE : period;
    }

    public boolean hasExplicitAccountSelection() {
      return benchmarkAccountsSubmitted && !accountIds.isEmpty();
    }
  }

  class InvalidPortfolioRequest extends IllegalArgumentException {
    public InvalidPortfolioRequest(String message) {
      super(message);
    }
  }

  class PortfolioNotFoundException extends RuntimeException {
    public PortfolioNotFoundException(Long portfolioId) {
      super("Portfolio not found: " + portfolioId);
    }
  }

  /** Public dashboard page contract backed by immutable API-owned view models. */
  record DashboardPageView(
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
      periods = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(periods);
    }
  }
}
