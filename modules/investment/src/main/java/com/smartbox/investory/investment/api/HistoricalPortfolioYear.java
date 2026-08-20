package com.smartbox.investory.investment.api;

import java.math.BigDecimal;

/** Immutable brokerage portfolio facts for one calendar year. */
public record HistoricalPortfolioYear(
    boolean complete,
    BigDecimal startMarketAssets,
    BigDecimal marketAssets,
    BigDecimal marketIncome,
    BigDecimal grossDeposits,
    BigDecimal grossWithdrawals,
    BigDecimal netContribution,
    BigDecimal netWithdrawal,
    BigDecimal marketReturn) {
  /** End-of-calendar-year market value from the December row for this year. */
  public BigDecimal endMarketAssets() {
    return marketAssets;
  }

  public static HistoricalPortfolioYear incomplete() {
    return new HistoricalPortfolioYear(false, null, null, null, null, null, null, null, null);
  }
}
