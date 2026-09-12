package com.smartbox.investory.accounting;

import java.math.BigDecimal;

/** Narrow, versioned ZUS rule set for the currently supported 2026 POC. */
public final class ZusRules2026 {
  public static final BigDecimal SOCIAL = new BigDecimal("1788.29");
  public static final BigDecimal HEALTH = new BigDecimal("1495.04");

  private ZusRules2026() {}

  public static ZusCalculationInput input(boolean qualifyingUop) {
    return new ZusCalculationInput(
        qualifyingUop ? BigDecimal.ZERO : SOCIAL,
        HEALTH,
        BigDecimal.ZERO,
        "2026_POC_HEALTH",
        qualifyingUop ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE");
  }
}
