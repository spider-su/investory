package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A paid contribution eligible for deduction; an obligation alone is not deductible. */
public record PaidContribution(
    String contributionType,
    LocalDate contributionPeriod,
    LocalDate paymentDate,
    BigDecimal paidAmount,
    BigDecimal deductibleAmount,
    Long bankTransactionId) {
  public PaidContribution {
    if (contributionType == null || contributionType.isBlank())
      throw new IllegalArgumentException("contribution type is required");
    if (paymentDate == null || paidAmount == null || paidAmount.signum() < 0)
      throw new IllegalArgumentException("paid contribution payment and amount are required");
    deductibleAmount = deductibleAmount == null ? paidAmount : deductibleAmount;
  }
}
