package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AccountingDateRulesTest {
  @Test
  void usesTheEarlierSaleOrIssueDateWhenTheyFallInDifferentMonths() {
    assertThat(
            AccountingDateRules.accountingPeriod(
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 2), null, false))
        .isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  void sameMonthPaymentDoesNotChangeTheInvoicePeriod() {
    assertThat(
            AccountingDateRules.accountingPeriod(
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 17),
                LocalDate.of(2026, 6, 28),
                false))
        .isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  void laterPaymentDoesNotMoveTheInvoiceIntoThePaymentMonth() {
    assertThat(
            AccountingDateRules.accountingPeriod(
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 8, 5),
                false))
        .isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  void prepaymentDoesNotMoveTheInvoiceIntoThePaymentMonth() {
    assertThat(
            AccountingDateRules.accountingPeriod(
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 5, 20),
                false))
        .isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  void correctionUsesIssueMonthEvenWhenSaleDateIsEarlier() {
    assertThat(
            AccountingDateRules.accountingPeriod(
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 2), null, true))
        .isEqualTo(LocalDate.of(2026, 7, 1));
  }

  @Test
  void requiresAnInvoiceDateInsteadOfUsingPaymentAsItsAccountingPeriod() {
    assertThatThrownBy(
            () ->
                AccountingDateRules.accountingPeriod(null, null, LocalDate.of(2026, 6, 15), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Sale or issue date is required");
  }
}
