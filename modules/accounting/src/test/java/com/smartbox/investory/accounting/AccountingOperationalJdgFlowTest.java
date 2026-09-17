package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingOperationalJdgFlowTest {
  private static final LocalDate PERIOD = LocalDate.of(2026, 9, 1);

  @Test
  void processesOneMonthFromNormalizedFactsToLockedSettlement() {
    var sale =
        new InvoiceRow(
            1,
            PERIOD,
            PERIOD,
            PERIOD,
            PERIOD,
            "SALE-SEP",
            "DE Customer",
            "SALE",
            "PLN",
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("1000.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("0.12"),
            "reviewed");
    var euSale =
        new AccountingVatTransaction(
            PERIOD,
            "source-1",
            "SALE-SEP",
            AccountingVatTransaction.Direction.SALE,
            VatTreatment.EU_B2B_REVERSE_CHARGE,
            "DE",
            "DE123456789",
            "VAT",
            "DE123456789",
            PERIOD,
            "VERIFIED",
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "KSeF");
    var context =
        new AccountingPeriodContext(
            PERIOD,
            true,
            false,
            "JDG",
            false,
            new BigDecimal("0.12"),
            true,
            true,
            new AccountingYearToDateContext(
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of()),
            ZusRules2026.input(false));

    var result =
        new DefaultAccountingMonthCalculator(mock())
            .calculate(
                new AccountingCalculationInput(
                    PERIOD,
                    List.of(sale),
                    List.of(),
                    List.of(),
                    new AccountingProfile(false),
                    AccountingCalculationInput.CalculationAdjustments.none(),
                    context,
                    List.of(euSale)));

    assertThat(result.complete()).isTrue();
    assertThat(result.ryczalt().calculatedTax()).isEqualByComparingTo("120");
    assertThat(new AccountingVatEuExporter().export(PERIOD, List.of(euSale)).status())
        .isEqualTo(AccountingVatEuExporter.Status.READY);

    var artifact =
        new AccountingFilingArtifact(
            AccountingFilingArtifact.Type.VAT_UE,
            PERIOD,
            "VAT_UE_POC_2026",
            "vat-ue".getBytes(),
            AccountingFilingFingerprint.sha256("vat-ue".getBytes()),
            Instant.parse("2026-10-01T10:00:00Z"),
            AccountingFilingArtifact.Status.VALID);
    assertThat(artifact.status()).isEqualTo(AccountingFilingArtifact.Status.VALID);

    for (var obligation : result.calculatedObligations()) {
      assertThat(
              AccountingObligationReconciliation.compare(
                      obligation.type(),
                      PERIOD,
                      obligation.amount(),
                      obligation.amount(),
                      obligation.amount(),
                      obligation.amount())
                  .status())
          .isEqualTo(AccountingObligationReconciliation.Status.SETTLED);
    }

    var lifecycle = new AccountingPeriodLifecycle();
    var status =
        lifecycle.transition(
            PeriodLifecycleStatus.OPEN,
            PeriodLifecycleStatus.READY_FOR_REVIEW,
            false,
            false,
            false);
    status = lifecycle.transition(status, PeriodLifecycleStatus.CONFIRMED, false, false, false);
    status = lifecycle.transition(status, PeriodLifecycleStatus.FILED, false, true, false);
    status = lifecycle.transition(status, PeriodLifecycleStatus.PAID, false, true, false);
    status = lifecycle.transition(status, PeriodLifecycleStatus.SETTLED, false, true, true);
    assertThat(lifecycle.transition(status, PeriodLifecycleStatus.LOCKED, false, true, true))
        .isEqualTo(PeriodLifecycleStatus.LOCKED);
  }
}
