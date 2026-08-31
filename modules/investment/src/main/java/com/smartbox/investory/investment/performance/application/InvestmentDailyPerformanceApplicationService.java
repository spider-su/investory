package com.smartbox.investory.investment.performance.application;

import com.smartbox.investory.investment.api.reporting.InvestmentDailyPerformanceApi;
import com.smartbox.investory.investment.performance.PortfolioService;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adapts accounting attribution into the public Investment read boundary. */
@Service
@RequiredArgsConstructor
public class InvestmentDailyPerformanceApplicationService implements InvestmentDailyPerformanceApi {
  private final PortfolioService portfolioService;

  @Override
  public Object load(LocalDate date, Set<Long> accountIds) {
    return portfolioService.dailyPerformanceDetail(date, accountIds);
  }
}
