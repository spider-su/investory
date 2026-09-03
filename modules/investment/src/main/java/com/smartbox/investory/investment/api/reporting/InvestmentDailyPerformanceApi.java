package com.smartbox.investory.investment.api.reporting;

import com.smartbox.investory.investment.api.reporting.model.DailyPerformanceDetail;
import java.time.LocalDate;
import java.util.Set;

/** UI-facing daily attribution read contract. */
public interface InvestmentDailyPerformanceApi {
  DailyPerformanceDetail load(Long portfolioId, LocalDate date, Set<Long> accountIds);
}
