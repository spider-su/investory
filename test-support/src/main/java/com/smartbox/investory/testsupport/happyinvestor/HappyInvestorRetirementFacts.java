package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;

/** Independent calendar checkpoints for the canonical retirement projection. */
public final class HappyInvestorRetirementFacts {
  public static final int CURRENT_YEAR = 2025;
  public static final int CURRENT_YEAR_AGE = 41;
  public static final int FIRST_PROJECTED_YEAR = 2026;
  public static final int FIRST_PROJECTED_AGE = 42;
  public static final int LAST_PROJECTED_YEAR = 2069;
  public static final int LAST_PROJECTED_AGE = 85;

  // Independent calculations from persisted plan and source-domain boundary facts.
  public static final BigDecimal BRIDGE_CASH_END = new BigDecimal("50000");
  public static final BigDecimal BRIDGE_BONDS_END = new BigDecimal("10000");
  public static final BigDecimal BRIDGE_EQUITIES_END = new BigDecimal("159307.015664");
  public static final BigDecimal BRIDGE_REAL_ESTATE_END = new BigDecimal("900000");
  public static final BigDecimal FIRST_PROJECTED_RENTAL_INCOME = new BigDecimal("78120");
  public static final BigDecimal FIRST_PROJECTED_EQUITY_RETURN = new BigDecimal("11151.49109648");
  public static final BigDecimal FIRST_PROJECTED_BOND_END = new BigDecimal("10350");
  public static final BigDecimal FIRST_PROJECTED_EQUITY_END = new BigDecimal("182458.50676048");
  public static final BigDecimal FIRST_PROJECTED_END_NET_WORTH = new BigDecimal("1142808.50676048");
  public static final BigDecimal FINAL_END_NET_WORTH = new BigDecimal("6491717.30566213");
  public static final int RETIREMENT_BOUNDARY_YEAR = 2044;
  public static final int PENSION_BOUNDARY_YEAR = 2051;

  private HappyInvestorRetirementFacts() {}
}
