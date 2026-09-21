package com.smartbox.investory.ryczalt.checker;

import com.smartbox.investory.ryczalt.domain.AccountingPeriod;
import java.util.ArrayList;
import java.util.List;

/** Small eligibility check for freezing; it does not calculate or settle anything. */
public final class PeriodCompletenessChecker {
  public CheckResult check(
      AccountingPeriod period, boolean calculationsCurrent, List<PaymentCheckResult> payments) {
    List<CheckIssue> issues = new ArrayList<>();
    if (!calculationsCurrent) {
      issues.add(
          new CheckIssue(
              CheckIssue.Code.DIRTY_CALCULATION, CheckSeverity.ERROR, period.period().toString()));
    }
    for (int index = 0; index < payments.size(); index++) {
      PaymentCheckResult payment = payments.get(index);
      if (payment.status() == PaymentCheckStatus.AMBIGUOUS) {
        issues.add(
            new CheckIssue(
                CheckIssue.Code.AMBIGUOUS_PAYMENT, CheckSeverity.ERROR, Integer.toString(index)));
      } else if (payment.status() != PaymentCheckStatus.PAID
          && payment.status() != PaymentCheckStatus.PAID_LATE) {
        issues.add(
            new CheckIssue(
                CheckIssue.Code.UNSETTLED_OBLIGATION,
                CheckSeverity.ERROR,
                Integer.toString(index)));
      }
    }
    return new CheckResult(issues.isEmpty(), issues);
  }
}
