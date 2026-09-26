package com.smartbox.investory.ryczalt.pit28;

import com.smartbox.investory.ryczalt.calculation.RoundingPolicy;
import java.math.BigDecimal;

/** Pure annual PIT-28 arithmetic. It does not access Spring, persistence, or FX providers. */
public final class Pit28Calculator {
  public Pit28Calculation calculate(Pit28AnnualFacts facts) {
    BigDecimal social =
        RoundingPolicy.roundDeductionAllocation(facts.deductibleSocialContributions());
    BigDecimal health =
        RoundingPolicy.roundDeductionAllocation(facts.deductibleHealthContributions());
    BigDecimal taxable =
        RoundingPolicy.roundRyczaltTaxableBase(
            facts.revenuePln().subtract(social).subtract(health).max(BigDecimal.ZERO));
    BigDecimal tax =
        RoundingPolicy.roundRyczaltTax(taxable.multiply(facts.ryczaltRate()).max(BigDecimal.ZERO));
    BigDecimal paid = RoundingPolicy.roundRyczaltTax(facts.ryczaltPaidDuringYear());
    BigDecimal difference = tax.subtract(paid);
    return new Pit28Calculation(
        facts.year(),
        facts.revenuePln(),
        social,
        health,
        taxable,
        facts.ryczaltRate(),
        tax,
        paid,
        difference.max(BigDecimal.ZERO),
        difference.negate().max(BigDecimal.ZERO));
  }
}
