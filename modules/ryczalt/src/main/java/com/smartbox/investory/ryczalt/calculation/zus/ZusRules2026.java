package com.smartbox.investory.ryczalt.calculation.zus;

import java.math.BigDecimal;

/** Narrow, explicit 2026 ZUS rule set used by the certified JDG scenarios. */
public final class ZusRules2026 {
  public static final String VERSION = "ZUS_2026_POC_V1";
  public static final BigDecimal LABOUR_FUND = new BigDecimal("138.47");
  public static final BigDecimal SOCIAL_INSURANCE = new BigDecimal("1649.82");
  public static final BigDecimal VOLUNTARY_SICKNESS = new BigDecimal("138.47");
  public static final BigDecimal FULL_JDG_SOCIAL = SOCIAL_INSURANCE.add(LABOUR_FUND);
  public static final BigDecimal HEALTH_LOW = new BigDecimal("498.35");
  public static final BigDecimal HEALTH_MEDIUM = new BigDecimal("830.58");
  public static final BigDecimal HEALTH_HIGH = new BigDecimal("1495.04");

  private ZusRules2026() {}

  public static HealthBand healthBand(BigDecimal ytdRevenueAfterPaidSocial) {
    BigDecimal revenue =
        ytdRevenueAfterPaidSocial == null ? BigDecimal.ZERO : ytdRevenueAfterPaidSocial;
    if (revenue.compareTo(new BigDecimal("60000")) <= 0) return HealthBand.LOW;
    if (revenue.compareTo(new BigDecimal("300000")) <= 0) return HealthBand.MEDIUM;
    return HealthBand.HIGH;
  }

  public static HealthBand healthBandAfterPaidSocial(
      BigDecimal ytdRevenue, BigDecimal paidSocialContributions) {
    BigDecimal revenue = ytdRevenue == null ? BigDecimal.ZERO : ytdRevenue;
    BigDecimal social = paidSocialContributions == null ? BigDecimal.ZERO : paidSocialContributions;
    return healthBand(revenue.subtract(social).max(BigDecimal.ZERO));
  }

  public enum HealthBand {
    LOW(HEALTH_LOW),
    MEDIUM(HEALTH_MEDIUM),
    HIGH(HEALTH_HIGH);

    private final BigDecimal monthlyAmount;

    HealthBand(BigDecimal monthlyAmount) {
      this.monthlyAmount = monthlyAmount;
    }

    public BigDecimal monthlyAmount() {
      return monthlyAmount;
    }
  }
}
