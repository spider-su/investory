package com.smartbox.investory.ryczalt.calculation.ryczalt;

import java.math.BigDecimal;

/** The small, explicit 2026 rule set currently required by the reference scenarios. */
public final class RyczaltRules2026 {
  public static final String VERSION = "RYCZALT_2026_POC_V1";
  public static final BigDecimal HEALTH_DEDUCTION_RATIO = new BigDecimal("0.50");

  private RyczaltRules2026() {}
}
