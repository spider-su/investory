package com.smartbox.investory.accounting;

import java.math.BigDecimal;

/** Narrow, versioned ZUS rule set for the currently supported 2026 POC. */
public final class ZusRules2026 {
  public static final String VERSION = "ZUS_2026_POC_V1";
  public static final BigDecimal FULL_JDG_SOCIAL = new BigDecimal("1788.29");
  public static final BigDecimal SOCIAL = new BigDecimal("1788.29");
  public static final BigDecimal HEALTH = new BigDecimal("1495.04");
  public static final BigDecimal HEALTH_LOW = new BigDecimal("498.35");
  public static final BigDecimal HEALTH_MEDIUM = new BigDecimal("830.58");

  private ZusRules2026() {}

  public static ZusCalculationInput input(boolean qualifyingUop) {
    return new ZusCalculationInput(
        qualifyingUop ? BigDecimal.ZERO : SOCIAL,
        HEALTH,
        BigDecimal.ZERO,
        "2026_POC_HEALTH",
        qualifyingUop ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE");
  }

  public static HealthBand healthBand(BigDecimal ytdRevenue) {
    BigDecimal revenue = ytdRevenue == null ? BigDecimal.ZERO : ytdRevenue;
    if (revenue.compareTo(new BigDecimal("60000")) <= 0) return HealthBand.LOW;
    if (revenue.compareTo(new BigDecimal("300000")) <= 0) return HealthBand.MEDIUM;
    return HealthBand.HIGH;
  }

  public enum HealthBand {
    LOW {
      @Override
      public BigDecimal monthlyAmount() {
        return HEALTH_LOW;
      }
    },
    MEDIUM {
      @Override
      public BigDecimal monthlyAmount() {
        return HEALTH_MEDIUM;
      }
    },
    HIGH {
      @Override
      public BigDecimal monthlyAmount() {
        return HEALTH;
      }
    };

    public abstract BigDecimal monthlyAmount();
  }
}
