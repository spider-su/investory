package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AccountingDueDatePolicyTest {
  private final AccountingDueDatePolicy policy = new AccountingDueDatePolicy();

  @Test
  void adjustsPpeAndVatDueDatesToWorkingDays() {
    assertThat(policy.dueDate(LocalDate.of(2026, 7, 1), "RYCZALT"))
        .isEqualTo(LocalDate.of(2026, 8, 20));
    assertThat(policy.dueDate(LocalDate.of(2026, 7, 1), "VAT_UE"))
        .isEqualTo(LocalDate.of(2026, 8, 25));
  }

  @Test
  void comparesPaymentWithCanonicalObligationIncludingJulyShortPayment() {
    BigDecimal expected = new BigDecimal("1495.04");
    assertThat(policy.paymentStatus(expected, BigDecimal.ZERO))
        .isEqualTo(AccountingPaymentStatus.NOT_PAID);
    assertThat(policy.paymentStatus(expected, new BigDecimal("1495.00")))
        .isEqualTo(AccountingPaymentStatus.PARTIAL);
    assertThat(policy.remainingAmount(expected, new BigDecimal("1495.00")))
        .isEqualByComparingTo("0.04");
    assertThat(policy.paymentStatus(expected, expected)).isEqualTo(AccountingPaymentStatus.PAID);
    assertThat(policy.paymentStatus(expected, new BigDecimal("1500.00")))
        .isEqualTo(AccountingPaymentStatus.PAID);
    assertThat(policy.remainingAmount(expected, new BigDecimal("1500.00"))).isZero();
  }

  @Test
  void treatsNonPositiveObligationAsAlreadyPaid() {
    assertThat(policy.paymentStatus(BigDecimal.ZERO, BigDecimal.ZERO))
        .isEqualTo(AccountingPaymentStatus.PAID);
    assertThat(policy.paymentStatus(new BigDecimal("-0.01"), BigDecimal.ZERO))
        .isEqualTo(AccountingPaymentStatus.PAID);
    assertThat(policy.remainingAmount(new BigDecimal("-0.01"), BigDecimal.ZERO)).isZero();
  }
}
