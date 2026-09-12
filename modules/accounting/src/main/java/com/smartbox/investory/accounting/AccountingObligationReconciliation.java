package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Three-way obligation comparison; bank payment is additional evidence, not authority settlement.
 */
public record AccountingObligationReconciliation(
    String obligationType,
    LocalDate period,
    BigDecimal calculatedAmount,
    BigDecimal filedAmount,
    BigDecimal authorityPostedAmount,
    BigDecimal bankPaidAmount,
    Status status) {
  public enum Status {
    MATCH,
    MISSING_FILING,
    MISSING_CONFIRMATION,
    AMOUNT_MISMATCH,
    REVIEW_REQUIRED,
    SETTLED
  }

  public static AccountingObligationReconciliation compare(
      String type,
      LocalDate period,
      BigDecimal calculated,
      BigDecimal filed,
      BigDecimal authority,
      BigDecimal bankPaid) {
    if (filed == null)
      return new AccountingObligationReconciliation(
          type, period, calculated, null, authority, bankPaid, Status.MISSING_FILING);
    if (authority == null)
      return new AccountingObligationReconciliation(
          type, period, calculated, filed, null, bankPaid, Status.MISSING_CONFIRMATION);
    if (!same(calculated, filed) || !same(filed, authority))
      return new AccountingObligationReconciliation(
          type, period, calculated, filed, authority, bankPaid, Status.AMOUNT_MISMATCH);
    return new AccountingObligationReconciliation(
        type, period, calculated, filed, authority, bankPaid, Status.SETTLED);
  }

  private static boolean same(BigDecimal left, BigDecimal right) {
    return left != null && right != null && left.compareTo(right) == 0;
  }
}
