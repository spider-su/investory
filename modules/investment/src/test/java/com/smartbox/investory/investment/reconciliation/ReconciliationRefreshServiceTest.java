package com.smartbox.investory.investment.reconciliation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.notifications.SystemAuditNotificationProducer;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reconciliation Refresh Service")
class ReconciliationRefreshServiceTest {

  @Mock private PortfolioProjectionService portfolioProjectionService;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private SystemAuditNotificationProducer notificationProducer;

  @DisplayName("import Refresh Is Disabled By Default Boundary")
  @Test
  void importRefreshIsDisabledByDefaultBoundary() {
    new ReconciliationRefreshService(
            portfolioProjectionService, jdbcTemplate, notificationProducer, false)
        .refreshAfterImport(1L);

    verifyNoInteractions(portfolioProjectionService);
  }

  @DisplayName("enabled Import Refresh Runs The Diagnostic Views")
  @Test
  void enabledImportRefreshRunsTheDiagnosticViews() {
    new ReconciliationRefreshService(
            portfolioProjectionService, jdbcTemplate, notificationProducer, true)
        .refreshAfterImport(1L);

    verify(portfolioProjectionService).refreshReconciliationViews();
  }

  @DisplayName("post Commit Audit Failure Does Not Escape The Async Import Follow Up")
  @Test
  void postCommitAuditFailureDoesNotEscapeTheAsyncImportFollowUp() {
    when(jdbcTemplate.queryForObject(
            "SELECT investory.run_system_audit(?, 'POST_IMPORT')", UUID.class, 1L))
        .thenThrow(new IllegalStateException("audit timeout"));

    assertDoesNotThrow(
        () ->
            new ReconciliationRefreshService(
                    portfolioProjectionService, jdbcTemplate, notificationProducer, true)
                .refreshAfterImport(1L));

    verify(jdbcTemplate)
        .queryForObject("SELECT investory.run_system_audit(?, 'POST_IMPORT')", UUID.class, 1L);
    verify(portfolioProjectionService).refreshReconciliationViews();
  }

  @DisplayName("disabled Refresh Does Not Audit Stale Reconciliation")
  @Test
  void disabledRefreshDoesNotAuditStaleReconciliation() {
    new ReconciliationRefreshService(
            portfolioProjectionService, jdbcTemplate, notificationProducer, false)
        .refreshAfterImport(1L);

    verifyNoInteractions(portfolioProjectionService, jdbcTemplate);
  }

  @DisplayName("failed Refresh Does Not Audit Potentially Stale Financial Reconciliation")
  @Test
  void failedRefreshDoesNotAuditPotentiallyStaleFinancialReconciliation() {
    doThrow(new IllegalStateException("reconciliation refresh timeout"))
        .when(portfolioProjectionService)
        .refreshReconciliationViews();

    new ReconciliationRefreshService(
            portfolioProjectionService, jdbcTemplate, notificationProducer, true)
        .refreshAfterImport(1L);

    verify(portfolioProjectionService).refreshReconciliationViews();
    verifyNoInteractions(jdbcTemplate);
  }
}
