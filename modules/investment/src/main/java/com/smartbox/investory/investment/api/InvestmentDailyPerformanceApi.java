package com.smartbox.investory.investment.api;

import java.time.LocalDate;
import java.util.Set;

/** UI-facing daily attribution read contract. */
public interface InvestmentDailyPerformanceApi {
  Object load(LocalDate date, Set<Long> accountIds);
}
