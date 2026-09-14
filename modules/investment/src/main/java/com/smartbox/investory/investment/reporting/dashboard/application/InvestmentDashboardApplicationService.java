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
    ReturnMetric totalReturn = performanceKpi.totalReturn();
    boolean available = totalReturn.status() == ReturnMetric.Status.AVAILABLE;
    String totalReturnDisplay =
        available
            ? DashboardPercentageFormatter.signedPercent(totalReturn.value().doubleValue() * 100)
            : "Unavailable";
    ReturnMetric historical = performanceKpi.historicalAnnualizedReturn();
    String historicalDisplay =
        historical.status() == ReturnMetric.Status.AVAILABLE
            ? DashboardPercentageFormatter.signedPercent(historical.value().doubleValue() * 100)
            : "Unavailable";
    String expectedDisplay =
        performanceKpi.expectedAnnualReturn() == null
            ? "Unavailable"
            : DashboardPercentageFormatter.signedPercent(
                performanceKpi.expectedAnnualReturn().doubleValue() * 100);
    return new InvestmentDashboardApi.PerformanceKpiView(
        available,
        available ? totalReturn.value() : null,
        totalReturnDisplay,
        performanceKpi.startDate(),
        historical.status() == ReturnMetric.Status.AVAILABLE ? historical.value() : null,
        historicalDisplay,
        performanceKpi.expectedAnnualReturn(),
        expectedDisplay,
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
