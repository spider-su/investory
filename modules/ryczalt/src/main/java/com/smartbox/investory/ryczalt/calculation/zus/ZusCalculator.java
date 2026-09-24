package com.smartbox.investory.ryczalt.calculation.zus;

import com.smartbox.investory.ryczalt.calculation.RoundingPolicy;
import java.math.BigDecimal;
import java.util.Objects;

/** Pure ZUS calculation for the supported JDG/UoP cases. */
public final class ZusCalculator {
  private final String ruleVersion;

  public ZusCalculator() {
    this(ZusRules2026.VERSION);
  }

  public ZusCalculator(String ruleVersion) {
    this.ruleVersion = Objects.requireNonNull(ruleVersion, "ruleVersion");
  }

  public ZusCalculationResult calculate(ZusCalculationInput input) {
    Objects.requireNonNull(input, "input");
    if (input.zusRegime() != null && !"JDG".equals(input.zusRegime())) {
      throw new IllegalArgumentException("Unsupported ZUS regime: " + input.zusRegime());
    }
    BigDecimal social =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial()
                .add(input.voluntarySickness() ? ZusRules2026.VOLUNTARY_SICKNESS : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal calculatedDeductibleSocial =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial()
                .subtract(ZusRules2026.LABOUR_FUND)
                .add(input.voluntarySickness() ? ZusRules2026.VOLUNTARY_SICKNESS : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal deductibleSocial =
        input.socialContributionDeduction() == null
            ? calculatedDeductibleSocial
            : input.socialContributionDeduction();
    BigDecimal health =
        input.jdgActive()
            ? input.healthContributionOverride() == null
                ? input.explicitHealthBand().monthlyAmount()
                : input.healthContributionOverride()
            : BigDecimal.ZERO;
    social = RoundingPolicy.roundZusContribution(social);
    health = RoundingPolicy.roundZusContribution(health);
    BigDecimal healthPaidForDeduction =
        RoundingPolicy.roundZusContribution(
            input.healthContributionPaidOverride() == null
                ? health
                : input.healthContributionPaidOverride());
    return new ZusCalculationResult(
        social,
        health,
        healthPaidForDeduction,
        RoundingPolicy.roundZusContribution(social.add(health)),
        RoundingPolicy.roundZusContribution(deductibleSocial),
        input.explicitHealthBand(),
        input.qualifyingUop() ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE",
        ruleVersion);
  }
}
