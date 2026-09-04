package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;

/** Stable whole-wealth checkpoints for the persisted HappyInvestor snapshot. */
public final class HappyInvestorProfileFacts {
  public static final BigDecimal MARKET_PORTFOLIO_VALUE = BigDecimal.ZERO;
  public static final BigDecimal LONG_TERM_ASSET_VALUE = HappyInvestorLongTermFacts.LONG_TERM_TOTAL;
  public static final BigDecimal TOTAL_NET_WORTH = new BigDecimal("1010000");
  public static final BigDecimal LIQUID_ASSETS = new BigDecimal("201326.867325");
  public static final BigDecimal ILLIQUID_ASSETS = new BigDecimal("960000");

  /** Planning-source income at the fixed 2025-12-31 read-model boundary. */
  public static final BigDecimal CURRENT_RENTAL_INCOME = new BigDecimal("68096.31306693");

  public static final BigDecimal CURRENT_BOND_INCOME = new BigDecimal("374.73678362");
  public static final BigDecimal RETIREMENT_RESERVE = new BigDecimal("50000");
  public static final BigDecimal INVESTMENT_CAPITAL = BigDecimal.ZERO;
  public static final BigDecimal MARKET_INCOME_YTD = BigDecimal.ZERO;
  public static final BigDecimal MARKET_ANNUAL_INCOME = BigDecimal.ZERO;
  public static final BigDecimal MARKET_NET_YIELD = BigDecimal.ZERO;
  public static final BigDecimal LONG_TERM_ANNUAL_INCOME =
      HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL;
  public static final BigDecimal LONG_TERM_NET_YIELD = new BigDecimal("0.07061448");
  public static final BigDecimal COMBINED_ANNUAL_INCOME = LONG_TERM_ANNUAL_INCOME;
  public static final BigDecimal COMBINED_NET_YIELD = LONG_TERM_NET_YIELD;
  public static final BigDecimal EQUITY_ALLOCATION = new BigDecimal("141326.867325");
  public static final BigDecimal REAL_ESTATE_ALLOCATION = new BigDecimal("900000");
  public static final BigDecimal CASH_ALLOCATION = new BigDecimal("100000");
  public static final BigDecimal OTHER_ALLOCATION = new BigDecimal("10000");
  public static final BigDecimal FIXED_INCOME_ALLOCATION = new BigDecimal("10000");

  private HappyInvestorProfileFacts() {}
}
