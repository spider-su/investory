package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process Java facade for Investment dashboard queries. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/investment/dashboard")
@RequiredArgsConstructor
public class InvestmentDashboardRestController {
  private final InvestmentDashboardApi dashboard;

  @PostMapping("/query")
  public InvestmentDashboardApi.DashboardPageView dashboard(
      @PathVariable @Positive Long portfolioId, @RequestBody DashboardRequest request) {
    return dashboard.loadDashboard(
        new InvestmentDashboardApi.DashboardQuery(
            request.accountIds(),
            request.benchmarkAccountsSubmitted(),
            request.period(),
            portfolioId));
  }

  @GetMapping("/performance-kpi")
  public InvestmentDashboardApi.PerformanceKpiView performanceKpi(
      @PathVariable @Positive Long portfolioId) {
    return dashboard.loadPerformanceKpi(portfolioId);
  }

  @GetMapping("/investment-result-ytd")
  public InvestmentDashboardApi.InvestmentResultView investmentResultYtd(
      @PathVariable @Positive Long portfolioId) {
    return dashboard.investmentResultYtd(portfolioId);
  }

  public record DashboardRequest(
      List<Long> accountIds, boolean benchmarkAccountsSubmitted, DashboardPeriod period) {}
}
