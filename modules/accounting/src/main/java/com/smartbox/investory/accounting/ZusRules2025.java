package com.smartbox.investory.accounting;

import java.math.BigDecimal;

/** Versioned 2025 contribution amounts for the supported JDG ryczałt profile. */
public final class ZusRules2025 {
  public static final String VERSION = "ZUS_2025_POC_V1";
  public static final BigDecimal SOCIAL_INSURANCE = new BigDecimal("1518.98");
  public static final BigDecimal LABOUR_FUND = new BigDecimal("127.49");
  public static final BigDecimal VOLUNTARY_SICKNESS = new BigDecimal("127.49");
  public static final BigDecimal FULL_JDG_SOCIAL = SOCIAL_INSURANCE.add(LABOUR_FUND);
  public static final BigDecimal HEALTH_LOW = new BigDecimal("461.66");
  public static final BigDecimal HEALTH_MEDIUM = new BigDecimal("769.43");
  public static final BigDecimal HEALTH_HIGH = new BigDecimal("1384.97");

  private ZusRules2025() {}
}
