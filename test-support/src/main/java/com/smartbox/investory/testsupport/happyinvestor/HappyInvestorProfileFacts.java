package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Stable whole-wealth checkpoints for the persisted HappyInvestor snapshot. */
public final class HappyInvestorProfileFacts {
  public static final LocalDate AS_OF_DATE = HappyInvestorBrokerFacts.AS_OF_DATE;

  /** Complete broker value at the fixed 2025-12-31 boundary, including boundary cash. */
  public static final BigDecimal MARKET_PORTFOLIO_VALUE =
      HappyInvestorBrokerFacts.MARKET_PORTFOLIO_VALUE;

  public static final BigDecimal BROKERAGE_CASH = HappyInvestorBrokerFacts.BROKERAGE_CASH;
  public static final BigDecimal LONG_TERM_ASSET_VALUE = HappyInvestorLongTermFacts.LONG_TERM_TOTAL;
  public static final BigDecimal TOTAL_NET_WORTH =
      MARKET_PORTFOLIO_VALUE.add(LONG_TERM_ASSET_VALUE);
  public static final BigDecimal RETIREMENT_RESERVE = new BigDecimal("25000");

  /** Only cash available at the valuation boundary is liquid; the 10K treasury matures later. */
  public static final BigDecimal LIQUID_ASSETS = MARKET_PORTFOLIO_VALUE.add(RETIREMENT_RESERVE);

  public static final BigDecimal ILLIQUID_ASSETS = new BigDecimal("945000");

  /** Planning-source income at the fixed 2025-12-31 read-model boundary. */
  public static final BigDecimal CURRENT_RENTAL_INCOME = new BigDecimal("73873.00");

  public static final BigDecimal CURRENT_BOND_INCOME = new BigDecimal("374.625");
  public static final BigDecimal INVESTMENT_CAPITAL = HappyInvestorBrokerFacts.OPEN_POSITIONS_VALUE;
  public static final BigDecimal MARKET_INCOME_YTD = new BigDecimal("1108.28422125");
  public static final BigDecimal MARKET_ANNUAL_INCOME = new BigDecimal("1108.28422125");
  public static final BigDecimal MARKET_NET_YIELD = new BigDecimal("0.00330799");
  public static final BigDecimal LONG_TERM_ANNUAL_INCOME =
      HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL;
  public static final BigDecimal LONG_TERM_NET_YIELD = new BigDecimal("0.07818503");
  public static final BigDecimal COMBINED_ANNUAL_INCOME =
      MARKET_ANNUAL_INCOME.add(LONG_TERM_ANNUAL_INCOME);
  public static final BigDecimal COMBINED_NET_YIELD = new BigDecimal("0.06711648");
  public static final BigDecimal EQUITY_ALLOCATION = new BigDecimal("174487.759664");
  public static final BigDecimal REAL_ESTATE_ALLOCATION = new BigDecimal("900000");
  public static final BigDecimal CASH_ALLOCATION = new BigDecimal("50000");
  public static final BigDecimal OTHER_ALLOCATION = new BigDecimal("10000");
  public static final BigDecimal FIXED_INCOME_ALLOCATION = new BigDecimal("10000");

  private HappyInvestorProfileFacts() {}
}
