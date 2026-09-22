package com.smartbox.investory.ryczalt.calculation.zus;

import com.smartbox.investory.ryczalt.calculation.RoundingPolicy;
import java.math.BigDecimal;
import java.util.Objects;

/** Pure ZUS calculation for the supported JDG/UoP cases. */
public final class ZusCalculator {
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
    BigDecimal deductibleSocial =
        input.jdgActive() && !input.qualifyingUop()
            ? input
                .fullJdgSocial()
                .subtract(ZusRules2026.LABOUR_FUND)
                .add(input.voluntarySickness() ? ZusRules2026.VOLUNTARY_SICKNESS : BigDecimal.ZERO)
            : BigDecimal.ZERO;
    BigDecimal health =
        input.jdgActive() ? input.explicitHealthBand().monthlyAmount() : BigDecimal.ZERO;
    social = RoundingPolicy.roundZusContribution(social);
    health = RoundingPolicy.roundZusContribution(health);
    return new ZusCalculationResult(
        social,
        health,
        RoundingPolicy.roundZusContribution(social.add(health)),
        RoundingPolicy.roundZusContribution(deductibleSocial),
        input.explicitHealthBand(),
        input.qualifyingUop() ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE",
        ZusRules2026.VERSION);
  }
}
