package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;

/** Explicit F3 checkpoints for the canonical Happy Investor reporting story. */
public final class HappyInvestorReportingFacts {
  public static final BigDecimal MIXED_CURRENCY_REALIZED_RESULT = new BigDecimal("138.00000000");
  public static final BigDecimal MIXED_CURRENCY_PLN_COMPONENT = new BigDecimal("400.00000000");
  public static final BigDecimal MIXED_CURRENCY_EUR_FEE = new BigDecimal("-8.00000000");

  private HappyInvestorReportingFacts() {}
}
