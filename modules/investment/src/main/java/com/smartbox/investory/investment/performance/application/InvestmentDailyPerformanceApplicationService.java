package com.smartbox.investory.investment.performance.application;

import com.smartbox.investory.investment.api.reporting.InvestmentDailyPerformanceApi;
import com.smartbox.investory.investment.performance.PortfolioMetricsService;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adapts accounting attribution into the public Investment read boundary. */
@Service
@RequiredArgsConstructor
public class InvestmentDailyPerformanceApplicationService implements InvestmentDailyPerformanceApi {
  private final PortfolioMetricsService portfolioMetricsService;

  @Override
  public com.smartbox.investory.investment.api.reporting.model.DailyPerformanceDetail load(
      LocalDate date, Set<Long> accountIds) {
    return portfolioMetricsService.dailyPerformanceDetail(date, accountIds);
  }
}
