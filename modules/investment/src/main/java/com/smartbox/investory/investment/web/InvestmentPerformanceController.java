package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceBoardQuery;
import com.smartbox.investory.investment.api.reporting.PerformanceAggregation;
import com.smartbox.investory.investment.api.reporting.PerformanceMetric;
import com.smartbox.investory.investment.api.reporting.PerformanceStyle;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the canonical Investment performance read model. */
@RestController
@RequestMapping("/api/v1/investment/performance")
@RequiredArgsConstructor
public class InvestmentPerformanceController {
  private final InvestmentPerformanceApi performanceApi;

  @GetMapping("/board")
  public InvestmentPerformanceApi.PerformanceBoardView performance(
      @RequestParam(required = false) String accountIds,
      @RequestParam(defaultValue = "monthly") PerformanceAggregation aggregation,
      @RequestParam(defaultValue = "return") PerformanceMetric metric,
      @RequestParam(defaultValue = "line") PerformanceStyle style,
      @RequestParam(required = false) DashboardPeriod period,
      @RequestParam Long portfolioId) {
    List<Long> ids = accountIds == null ? null : AccountIdParser.parse(accountIds);
    return performanceApi.load(
        new PerformanceBoardQuery(ids, aggregation, metric, style, period, portfolioId));
  }

  @GetMapping("/monthly")
  public InvestmentPerformanceApi.PerformanceBoardView monthlyPerformance(
      @RequestParam String accountIds,
      @RequestParam(defaultValue = "monthly") PerformanceAggregation aggregation,
      @RequestParam Long portfolioId) {
    List<Long> ids = AccountIdParser.parse(accountIds);
    return performanceApi.load(
        new PerformanceBoardQuery(
            ids, aggregation, PerformanceMetric.PROFIT, PerformanceStyle.BARS, null, portfolioId));
  }

  /** Compatibility alias for clients released before the monthly endpoint was named explicitly. */
  @Deprecated(forRemoval = false)
  @GetMapping("/account")
  public InvestmentPerformanceApi.PerformanceBoardView accountPerformance(
      @RequestParam String accountIds,
      @RequestParam(defaultValue = "monthly") PerformanceAggregation aggregation,
      @RequestParam Long portfolioId) {
    return monthlyPerformance(accountIds, aggregation, portfolioId);
  }
}
