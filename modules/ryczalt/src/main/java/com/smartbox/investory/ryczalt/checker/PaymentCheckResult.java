package com.smartbox.investory.ryczalt.checker;

import java.math.BigDecimal;
import java.util.List;

public record PaymentCheckResult(
    PaymentCheckStatus status,
    BigDecimal expectedAmount,
    BigDecimal matchedAmount,
    List<PaymentAllocation> matchedTransactions,
    List<PaymentIssue> issues) {
  public PaymentCheckResult {
    matchedTransactions = List.copyOf(matchedTransactions);
    issues = List.copyOf(issues);
  }
}
