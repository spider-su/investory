package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;

/** Named F4 facts maintained from the Happy Investor specification, not UI output. */
public final class HappyInvestorDashboardFacts {
  public static final String REPORTING_CURRENCY = HappyInvestorTestData.REPORTING_CURRENCY.name();
  public static final BigDecimal BALANCE = BigDecimal.ZERO;
  public static final BigDecimal NET_DEPOSITS = new BigDecimal("427285.84");
  public static final BigDecimal DEPOSITS = new BigDecimal("451127.99");
  public static final BigDecimal WITHDRAWALS = new BigDecimal("23842.15");
  public static final BigDecimal OPEN_POSITIONS_VALUE = new BigDecimal("141326.867325");
  public static final BigDecimal OPEN_POSITIONS_UNREALIZED = new BigDecimal("35788.367325");
  public static final BigDecimal OPEN_POSITIONS_RETURN_PERCENT =
      new BigDecimal("33.91024822695035");
  public static final BigDecimal APPLE_VALUE = new BigDecimal("139815.496125");
  public static final BigDecimal APPLE_UNREALIZED = new BigDecimal("35025.496125");
  public static final BigDecimal TESLA_VALUE = new BigDecimal("1511.3712");
  public static final BigDecimal TESLA_UNREALIZED = new BigDecimal("762.8712");
  public static final BigDecimal EQUITY_WEIGHT_PERCENT = new BigDecimal("100.0");

  /** Latest canonical rates visible at the fixed 2025-12-31 read-model boundary. */
  public static final BigDecimal USD_PER_PLN = new BigDecimal("0.27807804976747746");

  public static final BigDecimal EUR_PER_PLN = new BigDecimal("0.23659114374269372");

  private HappyInvestorDashboardFacts() {}
}
