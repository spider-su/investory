package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.DashboardPercentageFormatter;
import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQuery;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Adapter from the dashboard application composition to its public module contract. */
@Service
@Primary
@RequiredArgsConstructor
public class InvestmentDashboardApplicationService implements InvestmentDashboardApi {
  private final InvestmentDashboardFacade dashboard;
  private final PortfolioPerformanceQuery performance;
  private final PortfolioContextReader portfolios;
  private final Clock clock;

  @Override
  public InvestmentDashboardApi.DashboardPageView loadDashboard(
      InvestmentDashboardApi.DashboardQuery query) {
    requirePortfolio(query.portfolioId());
    return dashboard.loadDashboard(query);
  }

  @Override
  public InvestmentDashboardApi.PerformanceKpiView loadPerformanceKpi(Long portfolioId) {
    requirePortfolio(portfolioId);
    var performanceKpi = dashboard.loadPerformanceKpi(portfolioId);
    ReturnMetric annualized = performanceKpi.annualizedReturn();
    boolean available = annualized.status() == ReturnMetric.Status.AVAILABLE;
    String display =
        available
            ? DashboardPercentageFormatter.signedPercent(annualized.value().doubleValue() * 100)
            : "Unavailable";
    ReturnMetric ytdReturn = performanceKpi.totalReturn();
    return new InvestmentDashboardApi.PerformanceKpiView(
        available,
        available ? annualized.value() : null,
        display,
        performanceKpi.startDate(),
        ytdReturn.status() == ReturnMetric.Status.AVAILABLE ? ytdReturn.value() : null,
        performanceKpi.historicalAnnualizedReturn().status() == ReturnMetric.Status.AVAILABLE
            ? performanceKpi.historicalAnnualizedReturn().value()
            : null,
        performanceKpi.historicalAnnualizedReturn().status() == ReturnMetric.Status.AVAILABLE
            ? DashboardPercentageFormatter.signedPercent(
                performanceKpi.historicalAnnualizedReturn().value().doubleValue() * 100)
            : "Unavailable",
        performanceKpi.expectedAnnualReturn(),
        display,
        performanceKpi.historyYears(),
        performanceKpi.historyContext());
  }

  @Override
  public InvestmentDashboardApi.InvestmentResultView investmentResultYtd(Long portfolioId) {
    requirePortfolio(portfolioId);
    YearMonth asOf = YearMonth.now(clock);
    var result = performance.forPortfolioMonths(portfolioId, YearMonth.of(asOf.getYear(), 1), asOf);
    if (result.baseCurrency() == null) {
      return InvestmentDashboardApi.InvestmentResultView.unavailable(null);
    }
    return new InvestmentDashboardApi.InvestmentResultView(
        true, result.investmentResult(), result.baseCurrency());
  }

  private void requirePortfolio(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new InvestmentDashboardApi.InvalidPortfolioRequest("portfolioId must be positive");
    }
    if (portfolios.findById(portfolioId).isEmpty()) {
      throw new InvestmentDashboardApi.PortfolioNotFoundException(portfolioId);
    }
  }
}
