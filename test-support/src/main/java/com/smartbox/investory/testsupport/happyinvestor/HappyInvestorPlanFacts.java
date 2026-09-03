package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;

/** Independent persisted-plan facts for F12-F14. */
public final class HappyInvestorPlanFacts {
  public static final long SEED_PLAN_ID = 9201L;
  public static final long SEED_REVISION_ID = 9202L;
  public static final String NAME = "Happy Investor Plan";
  public static final int START_YEAR = 2024;
  public static final int CURRENT_AGE = 40;
  public static final int RETIREMENT_AGE = 60;
  public static final int END_AGE = 85;
  public static final BigDecimal ANNUAL_LIVING_EXPENSES = new BigDecimal("36000");
  public static final BigDecimal ANNUAL_DISCRETIONARY_EXPENSES = new BigDecimal("6000");
  public static final BigDecimal ANNUAL_EMPLOYMENT_INCOME = new BigDecimal("90000");
  public static final BigDecimal ANNUAL_PRE_RETIREMENT_CONTRIBUTION = new BigDecimal("12000");
  public static final BigDecimal ANNUAL_PENSION = new BigDecimal("24000");
  public static final int PENSION_START_AGE = 67;
  public static final BigDecimal INFLATION = new BigDecimal("0.025");
  public static final BigDecimal FIXED_INCOME_RETURN = new BigDecimal("0.04");
  public static final BigDecimal EQUITY_RETURN = new BigDecimal("0.07");
  public static final BigDecimal SAFE_RESERVE_YEARS = new BigDecimal("2");
  public static final BigDecimal HARVEST_THRESHOLD = new BigDecimal("0.05");
  public static final BigDecimal HARVEST_SHARE = new BigDecimal("0.25");
  public static final BigDecimal BASELINE_INVESTMENT_CAPITAL = new BigDecimal("159307.015664");
  public static final BigDecimal BASELINE_LONG_TERM_CAPITAL = new BigDecimal("970000");

  private HappyInvestorPlanFacts() {}
}
