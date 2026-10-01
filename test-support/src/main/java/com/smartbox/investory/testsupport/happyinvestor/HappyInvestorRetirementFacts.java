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
  public static final BigDecimal BRIDGE_CASH_END = new BigDecimal("25000");

  /** Current bond principal 10,000 after the plan's full 3.5% current-year return. */
  public static final BigDecimal BRIDGE_BONDS_END = new BigDecimal("10350");

  /** Investment API opening capital plus its 7% full-year result used by the live bridge. */
  public static final BigDecimal BRIDGE_EQUITIES_END =
      HappyInvestorProfileFacts.MARKET_INVESTMENT_INCOME_BASE.multiply(
          BigDecimal.ONE.add(HappyInvestorPlanFacts.EQUITY_RETURN));

  public static final BigDecimal BRIDGE_REAL_ESTATE_END = new BigDecimal("900000");

  /** Current net rental income 61,872 grown by the plan's 5% effective rental rate. */
  public static final BigDecimal FIRST_PROJECTED_RENTAL_INCOME = new BigDecimal("64965.6");

  public static final BigDecimal FIRST_PROJECTED_EQUITY_RETURN =
      BRIDGE_EQUITIES_END.multiply(HappyInvestorPlanFacts.EQUITY_RETURN);
  public static final BigDecimal FIRST_PROJECTED_BOND_END =
      BRIDGE_BONDS_END.multiply(BigDecimal.ONE.add(HappyInvestorPlanFacts.FIXED_INCOME_RETURN));
  public static final BigDecimal FIRST_PROJECTED_EQUITY_END =
      BRIDGE_EQUITIES_END
          .add(FIRST_PROJECTED_EQUITY_RETURN)
          .add(HappyInvestorPlanFacts.ANNUAL_PRE_RETIREMENT_CONTRIBUTION);
  public static final BigDecimal FIRST_PROJECTED_END_NET_WORTH =
      new BigDecimal("1425645.419855828854");
  public static final BigDecimal RETIREMENT_BOUNDARY_CORE_EXPENSES =
      HappyInvestorPlanFacts.ANNUAL_LIVING_EXPENSES
          .add(HappyInvestorPlanFacts.ANNUAL_DISCRETIONARY_EXPENSES)
          .multiply(
              BigDecimal.ONE
                  .add(HappyInvestorPlanFacts.INFLATION)
                  .add(HappyInvestorPlanFacts.SPENDING_GROWTH_SPREAD));

  /** Full-horizon result with the Investment API opening base and planned bond/equity returns. */
  public static final BigDecimal FINAL_END_NET_WORTH = new BigDecimal("12108731.77");

  public static final int RETIREMENT_BOUNDARY_YEAR = 2044;
  public static final int PENSION_BOUNDARY_YEAR = 2051;

  private HappyInvestorRetirementFacts() {}
}
