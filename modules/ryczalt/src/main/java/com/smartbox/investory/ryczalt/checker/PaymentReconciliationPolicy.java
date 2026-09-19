package com.smartbox.investory.ryczalt.checker;

import java.math.BigDecimal;

/** Settlement uses exact grosz comparison, matching AccountingObligationReconciliation. */
public final class PaymentReconciliationPolicy {
  private PaymentReconciliationPolicy() {}

  public static boolean sameObligationAmount(BigDecimal expected, BigDecimal paid) {
    return expected != null && paid != null && expected.compareTo(paid) == 0;
  }
}
