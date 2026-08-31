package com.smartbox.investory.testsupport.profile;

import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import java.math.BigDecimal;

/** Test fixtures for concise Profile income setup. */
public final class ProfileIncomeSummaryFixtures {
  private ProfileIncomeSummaryFixtures() {}

  public static ProfileIncomeSummary annualIncome(
      BigDecimal marketIncome,
      BigDecimal marketValue,
      BigDecimal longTermIncome,
      BigDecimal longTermValue,
      BigDecimal totalIncome,
      BigDecimal totalValue) {
    return new ProfileIncomeSummary(
        marketIncome,
        marketIncome,
        ProfileIncomeSummary.ratio(marketIncome, marketValue),
        longTermIncome,
        ProfileIncomeSummary.ratio(longTermIncome, longTermValue),
        totalIncome,
        ProfileIncomeSummary.ratio(totalIncome, totalValue));
  }
}
