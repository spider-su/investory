package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

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
}
