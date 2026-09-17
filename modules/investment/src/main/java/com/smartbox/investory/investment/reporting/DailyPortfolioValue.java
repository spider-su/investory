package com.smartbox.investory.investment.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Portfolio-level daily boundary assembled from canonical account_daily rows. */
public record DailyPortfolioValue(
    LocalDate date,
    BigDecimal endValue,
    BigDecimal contributions,
    BigDecimal withdrawals,
    BigDecimal initializationAdjustment) {

  public DailyPortfolioValue(
      LocalDate date, BigDecimal endValue, BigDecimal contributions, BigDecimal withdrawals) {
    this(date, endValue, contributions, withdrawals, BigDecimal.ZERO);
  }
}
