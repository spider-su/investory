package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingVatAndLifecycleTest {
  @Test
  void vatEuRequiresReviewedIdentityEvidenceAndIsNotRequiredWithoutRows() {
    var exporter = new AccountingVatEuExporter();
    assertThat(exporter.export(LocalDate.of(2026, 9, 1), List.of()).status())
        .isEqualTo(AccountingVatEuExporter.Status.NOT_REQUIRED);

    var row =
        new AccountingVatTransaction(
            LocalDate.of(2026, 9, 1),
            "source",
            "EU-1",
            AccountingVatTransaction.Direction.SALE,
            VatTreatment.EU_B2B_REVERSE_CHARGE,
            "DE",
            "DE123",
            "VAT",
            null,
            null,
            null,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "review");
    assertThat(exporter.export(row.taxDate(), List.of(row)).status())
        .isEqualTo(AccountingVatEuExporter.Status.REVIEW_REQUIRED);
  }

  @Test
  void lifecycleRequiresEvidenceForFilingAndSettlementAndReopenForLockedPeriod() {
    var lifecycle = new AccountingPeriodLifecycle();
    assertThatThrownBy(
            () ->
                lifecycle.transition(
                    PeriodLifecycleStatus.CONFIRMED,
                    PeriodLifecycleStatus.FILED,
                    false,
                    false,
                    false))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                lifecycle.transition(
                    PeriodLifecycleStatus.OPEN, PeriodLifecycleStatus.FILED, false, true, true))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> lifecycle.reopen(PeriodLifecycleStatus.SETTLED, "fix"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> lifecycle.reopen(PeriodLifecycleStatus.LOCKED, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(lifecycle.reopen(PeriodLifecycleStatus.LOCKED, "correct source"))
        .isEqualTo(PeriodLifecycleStatus.OPEN);
    assertThat(
            lifecycle.transition(
                lifecycle.transition(
                    PeriodLifecycleStatus.FILED, PeriodLifecycleStatus.PAID, false, true, false),
                PeriodLifecycleStatus.SETTLED,
                false,
                true,
                true))
        .isEqualTo(PeriodLifecycleStatus.SETTLED);
    assertThatThrownBy(
            () ->
                lifecycle.transition(
                    PeriodLifecycleStatus.LOCKED, PeriodLifecycleStatus.SETTLED, false, true, true))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void settlementRequiresCalculatedFiledAndAuthorityAmountsToAgree() {
    var reconciliation =
        AccountingObligationReconciliation.compare(
            "VAT",
            LocalDate.of(2026, 9, 1),
            new BigDecimal("100"),
            new BigDecimal("100"),
            new BigDecimal("100"),
            new BigDecimal("99.99"));

    assertThat(reconciliation.status())
        .isEqualTo(AccountingObligationReconciliation.Status.PAYMENT_MISMATCH);
    assertThat(
            AccountingObligationReconciliation.compare(
                    "VAT", reconciliation.period(), new BigDecimal("100"), null, null, null)
                .status())
        .isEqualTo(AccountingObligationReconciliation.Status.MISSING_FILING);
    assertThat(
            AccountingObligationReconciliation.compare(
                    "VAT",
                    reconciliation.period(),
                    new BigDecimal("100"),
                    new BigDecimal("100"),
                    new BigDecimal("100"),
                    null)
                .status())
        .isEqualTo(AccountingObligationReconciliation.Status.MISSING_PAYMENT);
    assertThat(
            AccountingObligationReconciliation.compare(
                    "VAT",
                    reconciliation.period(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null)
                .status())
        .isEqualTo(AccountingObligationReconciliation.Status.SETTLED);
  }
}
