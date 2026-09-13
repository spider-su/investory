package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class AccountingDueDatePolicy {
  public AccountingPaymentStatus paymentStatus(BigDecimal expected, BigDecimal paid) {
    requireNonNegative(expected, "expected");
    requireNonNegative(paid, "paid");
    if (paid.signum() == 0) return AccountingPaymentStatus.UNPAID;
    return paid.compareTo(expected) < 0
        ? AccountingPaymentStatus.PARTIAL
        : AccountingPaymentStatus.PAID;
  }

  public BigDecimal remainingAmount(BigDecimal expected, BigDecimal paid) {
    requireNonNegative(expected, "expected");
    requireNonNegative(paid, "paid");
    return expected.subtract(paid).max(BigDecimal.ZERO);
  }

  private void requireNonNegative(BigDecimal value, String name) {
    if (value == null || value.signum() < 0) {
      throw new IllegalArgumentException(name + " amount must be non-negative");
    }
  }
}
