package com.smartbox.investory.investment.api.reporting;

import com.smartbox.investory.investment.api.reporting.model.DailyPerformanceDetail;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** UI-facing daily attribution read contract. */
public interface InvestmentDailyPerformanceApi {
  DailyPerformanceDetail load(Long portfolioId, LocalDate date, Set<Long> accountIds);

  List<DailyTickerMove> movers(Long portfolioId, LocalDate date, double thresholdPct, int limit);

  record DailyTickerMove(String symbol, double changePct) {}
}
