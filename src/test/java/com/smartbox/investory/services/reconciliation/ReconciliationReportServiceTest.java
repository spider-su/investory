package com.smartbox.investory.services.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.infrastructure.repository.reconciliation.ReconciliationReportRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationReportServiceTest {

  private final ReconciliationReportRepository repository = mock();
  private final ReconciliationReportService service = new ReconciliationReportService(repository);

  @Test
  void ordersIssuesByCheckpointAndBlocksLaterFailuresWithoutErasingOriginalStatus() {
    var position = mock(ReconciliationReportRepository.PositionIssueRow.class);
    when(position.getProvider()).thenReturn("IBKR");
    when(position.getAccountName()).thenReturn("Main");
    when(position.getSymbol()).thenReturn("VWCE");
    when(position.getValuationDate()).thenReturn(LocalDate.of(2026, 8, 14));
    when(position.getSeverity()).thenReturn("ERROR");
    when(position.getValidationCode()).thenReturn("MISSING_FX");
    when(position.getExpectedValue()).thenReturn(BigDecimal.TEN);
    when(position.getActualValue()).thenReturn(BigDecimal.ONE);
    when(position.getDifference()).thenReturn(BigDecimal.valueOf(-9));
    when(position.getMessage()).thenReturn("FX rate is missing");

    var account = mock(ReconciliationReportRepository.AccountIssueRow.class);
    when(account.getProvider()).thenReturn("IBKR");
    when(account.getAccountName()).thenReturn("Main");
    when(account.getValuationDate()).thenReturn(LocalDate.of(2026, 8, 14));
    when(account.getStatus()).thenReturn("FAIL");
    when(account.getDiagnosticCode()).thenReturn("ACCOUNT_DAILY_CASH_RECONCILIATION");
    when(account.getValidationMessage()).thenReturn("cash mismatch");
    when(account.getExpectedCashBalance()).thenReturn(BigDecimal.valueOf(100_000));
    when(account.getActualCashBalance()).thenReturn(BigDecimal.valueOf(99_200));
    when(account.getCashDifference()).thenReturn(BigDecimal.valueOf(-800));

    when(repository.findPositionIssues()).thenReturn(List.of(position));
    when(repository.findAccountIssues()).thenReturn(List.of(account));

    ReconciliationReport report = service.load();

    assertThat(report.overallState()).isEqualTo("UNRECONCILED");
    assertThat(report.firstFailingCheckpoint())
        .isEqualTo(new ReconciliationReport.Checkpoint("C3", "prices + FX", 1, 1));
    assertThat(report.issues())
        .extracting(ReconciliationReport.Issue::status)
        .containsExactly("FAIL", "BLOCKED");
    assertThat(report.issues().get(1).originalStatus()).isEqualTo(ReconciliationStatus.FAIL);
    assertThat(report.issues().getFirst())
        .satisfies(
            issue -> {
              assertThat(issue.level()).isEqualTo("C3 — prices + FX");
              assertThat(issue.location()).isEqualTo("IBKR / Main / VWCE / 2026-08-14");
              assertThat(issue.checkCode()).isEqualTo("MISSING_FX");
            });
    assertThat(report.issues().get(1))
        .satisfies(
            issue -> {
              assertThat(issue.level()).isEqualTo("C4 — account_daily");
              assertThat(issue.checkCode()).isEqualTo("ACCOUNT_DAILY_CASH_RECONCILIATION");
              assertThat(issue.expected()).isEqualByComparingTo("100000");
              assertThat(issue.actual()).isEqualByComparingTo("99200");
              assertThat(issue.difference()).isEqualByComparingTo("-800");
            });
    assertThat(report.checkpoints()).hasSize(8);
    assertThat(report.issueGroups())
        .extracting(ReconciliationReport.IssueGroup::level)
        .containsExactly("C3 — prices + FX", "C4 — account_daily");
  }

  @Test
  void reportsReviewWhenImplementedChecksAreCleanButCoverageIsIncomplete() {
    when(repository.findPositionIssues()).thenReturn(List.of());
    when(repository.findAccountIssues()).thenReturn(List.of());

    ReconciliationReport report = service.load();

    assertThat(report.overallState()).isEqualTo("REVIEW");
    assertThat(report.firstFailingCheckpoint()).isNull();
    assertThat(report.issues()).isEmpty();
    assertThat(report.checkpoints())
        .filteredOn(
            checkpoint ->
                checkpoint.checkpoint() != ReconciliationCheckpoint.C3
                    && checkpoint.checkpoint() != ReconciliationCheckpoint.C4)
        .allSatisfy(
            checkpoint ->
                assertThat(checkpoint.status()).isEqualTo(ReconciliationStatus.NOT_CHECKED));
    assertThat(report.checkpoints().get(3).status()).isEqualTo(ReconciliationStatus.PASS);
    assertThat(report.checkpoints().get(4).status()).isEqualTo(ReconciliationStatus.PASS);
  }

  @Test
  void unknownAccountDiagnosticRemainsExplicitInsteadOfBecomingEquity() {
    var account = mock(ReconciliationReportRepository.AccountIssueRow.class);
    when(account.getProvider()).thenReturn("XTB");
    when(account.getAccountName()).thenReturn("Main");
    when(account.getValuationDate()).thenReturn(LocalDate.of(2026, 8, 14));
    when(account.getStatus()).thenReturn("FAIL");
    when(account.getDiagnosticCode()).thenReturn("UNSEEN_DIAGNOSTIC");
    when(repository.findPositionIssues()).thenReturn(List.of());
    when(repository.findAccountIssues()).thenReturn(List.of(account));

    ReconciliationReport report = service.load();

    assertThat(report.issues().getFirst().checkCode()).isEqualTo("UNSEEN_DIAGNOSTIC");
    assertThat(report.issues().getFirst().checkName()).contains("Unknown diagnostic code");
    assertThat(report.issues().getFirst().expected()).isNull();
  }
}
