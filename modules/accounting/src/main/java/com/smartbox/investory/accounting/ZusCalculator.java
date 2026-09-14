package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure 2026 ZUS policy for the supported JDG case. */
public final class ZusCalculator {
  public ZusCalculation calculate(Input input) {
    if (input.zusRegime() != null && !"JDG".equals(input.zusRegime())) {
      throw new IllegalArgumentException("Unsupported ZUS regime: " + input.zusRegime());
    }
    ZusRules2026.HealthBand band = input.explicitHealthBand();
    BigDecimal social =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial()
                .add(input.voluntarySickness() ? ZusRules2026.VOLUNTARY_SICKNESS : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal deductibleSocial =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial()
                .subtract(ZusRules2026.LABOUR_FUND)
                .add(input.voluntarySickness() ? ZusRules2026.VOLUNTARY_SICKNESS : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal health = input.jdgActive() ? band.monthlyAmount() : BigDecimal.ZERO;
    return new ZusCalculation(
        social.setScale(2, RoundingMode.HALF_UP),
        health.setScale(2, RoundingMode.HALF_UP),
        social.add(health).setScale(2, RoundingMode.HALF_UP),
        band,
        input.qualifyingUop() ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE",
        ZusRules2026.VERSION,
        deductibleSocial.setScale(2, RoundingMode.HALF_UP));
  }

  public record Input(
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      BigDecimal ytdRyczaltRevenue,
      BigDecimal fullJdgSocial,
      ZusRules2026.HealthBand explicitHealthBand) {
    public Input {
      ytdRyczaltRevenue = ytdRyczaltRevenue == null ? BigDecimal.ZERO : ytdRyczaltRevenue;
      fullJdgSocial = fullJdgSocial == null ? ZusRules2026.FULL_JDG_SOCIAL : fullJdgSocial;
      explicitHealthBand =
          explicitHealthBand == null
              ? ZusRules2026.healthBand(ytdRyczaltRevenue)
              : explicitHealthBand;
    }

    public Input(
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

  public record ZusCalculation(
      BigDecimal socialContribution,
      BigDecimal healthContribution,
      BigDecimal totalObligation,
      ZusRules2026.HealthBand healthBand,
      String reason,
      String ruleVersion,
      BigDecimal deductibleSocialContribution) {}
}
