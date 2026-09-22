package com.smartbox.investory.ryczalt.calculation.zus;

import java.math.BigDecimal;

public record ZusCalculationInput(
    boolean jdgActive,
    boolean qualifyingUop,
    String zusRegime,
    boolean voluntarySickness,
    BigDecimal ytdRyczaltRevenue,
    BigDecimal fullJdgSocial,
    ZusRules2026.HealthBand explicitHealthBand) {
  public ZusCalculationInput {
    ytdRyczaltRevenue = ytdRyczaltRevenue == null ? BigDecimal.ZERO : ytdRyczaltRevenue;
    fullJdgSocial = fullJdgSocial == null ? ZusRules2026.FULL_JDG_SOCIAL : fullJdgSocial;
    explicitHealthBand =
        explicitHealthBand == null
            ? ZusRules2026.healthBand(ytdRyczaltRevenue)
            : explicitHealthBand;
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
        null);
  }
}
