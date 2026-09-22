package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure ZUS calculation for the supported JDG case with current-rule defaults. */
public final class ZusCalculator {
  public ZusCalculation calculate(Input input) {
    return calculate(
        input,
        ZusRules2026.LABOUR_FUND,
        ZusRules2026.VOLUNTARY_SICKNESS,
        ZusRules2026.HEALTH,
        ZusRules2026.VERSION);
  }

  public ZusCalculation calculate(
      Input input,
      BigDecimal labourFund,
      BigDecimal voluntarySicknessAmount,
      BigDecimal highBandHealthAmount,
      String ruleVersion) {
    if (input.zusRegime() != null && !"JDG".equals(input.zusRegime())) {
      throw new IllegalArgumentException("Unsupported ZUS regime: " + input.zusRegime());
    }
    ZusRules2026.HealthBand band = input.explicitHealthBand();
    BigDecimal social =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial()
                .add(input.voluntarySickness() ? voluntarySicknessAmount : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal deductibleSocial =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial()
                .subtract(labourFund)
                .add(input.voluntarySickness() ? voluntarySicknessAmount : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal health =
        input.jdgActive()
            ? band == ZusRules2026.HealthBand.HIGH ? highBandHealthAmount : band.monthlyAmount()
            : BigDecimal.ZERO;
    return new ZusCalculation(
        social.setScale(2, RoundingMode.HALF_UP),
        health.setScale(2, RoundingMode.HALF_UP),
        social.add(health).setScale(2, RoundingMode.HALF_UP),
        band,
        input.qualifyingUop() ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE",
        ruleVersion,
        deductibleSocial.setScale(2, RoundingMode.HALF_UP));
  }

  public ZusCalculation calculate(Input input, ZusAnnualRuleSet rules) {
    return calculate(
        input,
        rules.labourFund(),
        rules.voluntarySickness(),
        rules.health(input.explicitHealthBand()),
        rules.version());
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
