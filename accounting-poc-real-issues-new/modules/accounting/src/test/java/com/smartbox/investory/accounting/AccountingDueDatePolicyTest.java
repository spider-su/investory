package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountingDueDatePolicyTest {
  private final AccountingDueDatePolicy policy = new AccountingDueDatePolicy();

  @Test
  void classifiesUnpaid() {
    assertThat(policy.paymentStatus(new BigDecimal("1495.04"), BigDecimal.ZERO))
        .isEqualTo(AccountingPaymentStatus.UNPAID);
    assertThat(policy.remainingAmount(new BigDecimal("1495.04"), BigDecimal.ZERO))
        .isEqualByComparingTo("1495.04");
  }

  @Test
  void classifiesJulyZUSAsPartial() {
    assertThat(policy.paymentStatus(new BigDecimal("1495.04"), new BigDecimal("1495.00")))
        .isEqualTo(AccountingPaymentStatus.PARTIAL);
    assertThat(policy.remainingAmount(new BigDecimal("1495.04"), new BigDecimal("1495.00")))
        .isEqualByComparingTo("0.04");
  }

  @Test
  void classifiesExactAndOverpaymentAsPaid() {
    assertThat(policy.paymentStatus(new BigDecimal("10.00"), new BigDecimal("10.00")))
        .isEqualTo(AccountingPaymentStatus.PAID);
    assertThat(policy.paymentStatus(new BigDecimal("10.00"), new BigDecimal("11.00")))
        .isEqualTo(AccountingPaymentStatus.PAID);
    assertThat(policy.remainingAmount(new BigDecimal("10.00"), new BigDecimal("11.00"))).isZero();
  }

  @Test
  void rejectsNegativeAmounts() {
    assertThatThrownBy(() -> policy.paymentStatus(new BigDecimal("10"), new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
