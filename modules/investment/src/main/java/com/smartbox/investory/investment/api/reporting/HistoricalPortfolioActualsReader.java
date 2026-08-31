package com.smartbox.investory.investment.api.reporting;

/** Reads exact, complete calendar-year brokerage portfolio facts for planning. */
public interface HistoricalPortfolioActualsReader {
  HistoricalPortfolioYear read(Long portfolioId, int year);
}
