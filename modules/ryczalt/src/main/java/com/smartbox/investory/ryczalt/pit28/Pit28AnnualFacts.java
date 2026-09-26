package com.smartbox.investory.ryczalt.pit28;

import java.math.BigDecimal;
import java.util.Map;

public record Pit28AnnualFacts(
    int year,
    BigDecimal revenuePln,
    Map<String, BigDecimal> revenueByOriginalCurrency,
    BigDecimal socialContributionsPaid,
    BigDecimal deductibleSocialContributions,
    BigDecimal healthContributionsPaid,
    BigDecimal deductibleHealthContributions,
    BigDecimal ryczaltRate,
    BigDecimal ryczaltPaidDuringYear) {
  public Pit28AnnualFacts {
    revenuePln = value(revenuePln);
    revenueByOriginalCurrency = Map.copyOf(revenueByOriginalCurrency);
    socialContributionsPaid = value(socialContributionsPaid);
    deductibleSocialContributions = value(deductibleSocialContributions);
    healthContributionsPaid = value(healthContributionsPaid);
    deductibleHealthContributions = value(deductibleHealthContributions);
    ryczaltPaidDuringYear = value(ryczaltPaidDuringYear);
  }

  private static BigDecimal value(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
