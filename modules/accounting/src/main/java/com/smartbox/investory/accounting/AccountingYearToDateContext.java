package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.util.List;

/** Minimal accumulated context needed for deterministic monthly tax calculation. */
public record AccountingYearToDateContext(
    BigDecimal taxableRyczaltRevenue,
    BigDecimal paidSocialContributions,
    BigDecimal paidHealthContributions,
    BigDecimal deductionsAlreadyConsumed,
    List<PaidContribution> paidContributions) {
  public AccountingYearToDateContext {
    taxableRyczaltRevenue = zero(taxableRyczaltRevenue);
    paidSocialContributions = zero(paidSocialContributions);
    paidHealthContributions = zero(paidHealthContributions);
    deductionsAlreadyConsumed = zero(deductionsAlreadyConsumed);
    paidContributions = paidContributions == null ? List.of() : List.copyOf(paidContributions);
  }

  public static AccountingYearToDateContext empty() {
    return new AccountingYearToDateContext(null, null, null, null, List.of());
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
