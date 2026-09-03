package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process Java facade for Investment dashboard queries. */
@RestController
@Validated
@RequestMapping("/api/v1/investment/dashboard")
@RequiredArgsConstructor
public class InvestmentDashboardRestController {
  private final InvestmentDashboardApi dashboard;

  @PostMapping("/query")
  public InvestmentDashboardApi.DashboardPageView dashboard(
      @RequestBody InvestmentDashboardApi.DashboardQuery query) {
    return dashboard.loadDashboard(query);
  }

  @GetMapping("/performance-kpi")
  public InvestmentDashboardApi.PerformanceKpiView performanceKpi(
      @RequestParam @Positive Long portfolioId) {
    return dashboard.loadPerformanceKpi(portfolioId);
  }

  @GetMapping("/investment-result")
  public InvestmentDashboardApi.InvestmentResultView investmentResult(
      @RequestParam @Positive Long portfolioId) {
    return dashboard.investmentResult(portfolioId);
  }
}
