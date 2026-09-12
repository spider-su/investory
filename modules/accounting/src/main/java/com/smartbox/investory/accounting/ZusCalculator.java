package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure 2026 ZUS policy for the supported JDG case. */
public final class ZusCalculator {
  public ZusCalculation calculate(Input input) {
    ZusRules2026.HealthBand band = ZusRules2026.healthBand(input.ytdRyczaltRevenue());
    BigDecimal social =
        input.jdgActive() && !input.qualifyingUop() ? input.fullJdgSocial() : BigDecimal.ZERO;
    BigDecimal health = input.jdgActive() ? band.monthlyAmount() : BigDecimal.ZERO;
    return new ZusCalculation(
        social.setScale(2, RoundingMode.HALF_UP),
        health.setScale(2, RoundingMode.HALF_UP),
        social.add(health).setScale(2, RoundingMode.HALF_UP),
        band,
        input.qualifyingUop() ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE",
        ZusRules2026.VERSION);
  }

  public record Input(
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      BigDecimal ytdRyczaltRevenue,
      BigDecimal fullJdgSocial) {
    public Input {
      ytdRyczaltRevenue = ytdRyczaltRevenue == null ? BigDecimal.ZERO : ytdRyczaltRevenue;
      fullJdgSocial = fullJdgSocial == null ? ZusRules2026.FULL_JDG_SOCIAL : fullJdgSocial;
    }
  }

  public record ZusCalculation(
      BigDecimal socialContribution,
      BigDecimal healthContribution,
      BigDecimal totalObligation,
      ZusRules2026.HealthBand healthBand,
      String reason,
      String ruleVersion) {}
}
