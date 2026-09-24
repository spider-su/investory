package com.smartbox.investory.ryczalt.calculation.zus;

import java.math.BigDecimal;

public record ZusCalculationInput(
    boolean jdgActive,
    boolean qualifyingUop,
    String zusRegime,
    boolean voluntarySickness,
    BigDecimal ytdRyczaltRevenue,
    BigDecimal fullJdgSocial,
    ZusRules2026.HealthBand explicitHealthBand,
    BigDecimal socialContributionDeduction,
    BigDecimal healthContributionOverride,
    BigDecimal healthContributionPaidOverride) {
  public ZusCalculationInput {
    ytdRyczaltRevenue = ytdRyczaltRevenue == null ? BigDecimal.ZERO : ytdRyczaltRevenue;
    fullJdgSocial = fullJdgSocial == null ? ZusRules2026.FULL_JDG_SOCIAL : fullJdgSocial;
    explicitHealthBand =
        explicitHealthBand == null
            ? ZusRules2026.healthBand(ytdRyczaltRevenue)
            : explicitHealthBand;
    if (socialContributionDeduction != null && socialContributionDeduction.signum() < 0)
      throw new IllegalArgumentException("socialContributionDeduction must not be negative");
    if (healthContributionOverride != null && healthContributionOverride.signum() < 0)
      throw new IllegalArgumentException("healthContributionOverride must not be negative");
    if (healthContributionPaidOverride != null && healthContributionPaidOverride.signum() < 0)
      throw new IllegalArgumentException("healthContributionPaidOverride must not be negative");
  }

  public ZusCalculationInput(
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      BigDecimal ytdRyczaltRevenue,
      BigDecimal fullJdgSocial,
      ZusRules2026.HealthBand explicitHealthBand) {
    this(
        jdgActive,
        qualifyingUop,
        zusRegime,
        voluntarySickness,
        ytdRyczaltRevenue,
        fullJdgSocial,
        explicitHealthBand,
        null,
        null,
        null);
  }

  public ZusCalculationInput(
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      BigDecimal ytdRyczaltRevenue,
      BigDecimal fullJdgSocial,
      ZusRules2026.HealthBand explicitHealthBand,
      BigDecimal socialContributionDeduction,
      BigDecimal healthContributionOverride) {
    this(
        jdgActive,
        qualifyingUop,
        zusRegime,
        voluntarySickness,
        ytdRyczaltRevenue,
        fullJdgSocial,
        explicitHealthBand,
        socialContributionDeduction,
        healthContributionOverride,
        null);
  }

  public ZusCalculationInput(
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      BigDecimal ytdRyczaltRevenue,
      BigDecimal fullJdgSocial) {
    this(
        jdgActive,
        qualifyingUop,
        zusRegime,
        voluntarySickness,
        ytdRyczaltRevenue,
        fullJdgSocial,
        null,
        null,
        null,
        null);
  }
}
