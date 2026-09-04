package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;

/** Independently specified F2 checkpoints; production projection code must not create these. */
public final class HappyInvestorDailyFacts {
  public static final BigDecimal CASH_BALANCE = new BigDecimal("927.00000000");
  public static final BigDecimal MARKET_VALUE = new BigDecimal("216.00000000");
  public static final BigDecimal EQUITY = new BigDecimal("1143.00000000");
  public static final BigDecimal DIVIDENDS = new BigDecimal("9.00000000");
  public static final BigDecimal DEPOSITS = new BigDecimal("900.00000000");
  public static final BigDecimal REALIZED_PROFIT = new BigDecimal("18.00000000");

  private HappyInvestorDailyFacts() {}
}
