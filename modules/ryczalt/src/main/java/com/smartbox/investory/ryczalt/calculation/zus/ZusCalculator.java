package com.smartbox.investory.ryczalt.calculation.zus;

import com.smartbox.investory.ryczalt.calculation.RoundingPolicy;
import java.math.BigDecimal;
import java.util.Objects;

/** Pure ZUS calculation for the supported JDG/UoP cases. */
public final class ZusCalculator {
  public ZusCalculationResult calculate(ZusCalculationInput input) {
    return calculate(input, ZusRules2026.ruleSet());
  }

  public ZusCalculationResult calculate(ZusCalculationInput input, ZusRuleSet rules) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(rules, "rules");
    if (input.zusRegime() != null && !"JDG".equals(input.zusRegime())) {
      throw new IllegalArgumentException("Unsupported ZUS regime: " + input.zusRegime());
    }
    BigDecimal social =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial(rules)
                .add(input.voluntarySickness() ? rules.voluntarySickness() : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal deductibleSocial =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial(rules)
                .subtract(rules.labourFund())
                .add(input.voluntarySickness() ? rules.voluntarySickness() : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal health =
        input.jdgActive() ? rules.health(input.ytdRyczaltRevenue()) : BigDecimal.ZERO;
    social = RoundingPolicy.roundZusContribution(social);
    health = RoundingPolicy.roundZusContribution(health);
    return new ZusCalculationResult(
        social,
        health,
        RoundingPolicy.roundZusContribution(social.add(health)),
        RoundingPolicy.roundZusContribution(deductibleSocial),
        healthBand(input.ytdRyczaltRevenue(), rules),
        input.qualifyingUop() ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE",
        rules.version());
  }

  private ZusRules2026.HealthBand healthBand(BigDecimal ytdRevenue, ZusRuleSet rules) {
    if (ytdRevenue.compareTo(rules.thresholdLow()) <= 0) {
      return ZusRules2026.HealthBand.LOW;
    }
    if (ytdRevenue.compareTo(rules.thresholdMedium()) <= 0) {
      return ZusRules2026.HealthBand.MEDIUM;
    }
    return ZusRules2026.HealthBand.HIGH;
  }
}
