package com.smartbox.investory.investment.api.portfolio;

import java.time.YearMonth;

/** Public read boundary over the current brokerage reporting aggregation. */
public interface BrokeragePortfolioReader {
  SharedBrokeragePortfolioSnapshot currentSharedSnapshot();

  SharedBrokeragePortfolioSnapshot currentSnapshot(Long portfolioId);

  BrokerageIncomeSnapshot incomeForMonths(YearMonth from, YearMonth to);

  BrokerageIncomeSnapshot incomeForMonths(Long portfolioId, YearMonth from, YearMonth to);
}
