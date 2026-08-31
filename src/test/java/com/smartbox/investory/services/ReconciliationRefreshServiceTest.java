package com.smartbox.investory.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ReconciliationRefreshServiceTest {

  @Mock private PortfolioProjectionService portfolioProjectionService;
  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void importRefreshIsDisabledByDefaultBoundary() {
    new ReconciliationRefreshService(portfolioProjectionService, false).refreshAfterImport(1L);

    verifyNoInteractions(portfolioProjectionService);
  }

  @Test
  void enabledImportRefreshRunsTheDiagnosticViews() {
    new ReconciliationRefreshService(portfolioProjectionService, true).refreshAfterImport(1L);

    verify(portfolioProjectionService).refreshReconciliationViews();
  }

  @Test
  void postCommitAuditFailureDoesNotEscapeTheAsyncImportFollowUp() {
    when(jdbcTemplate.queryForObject(
            "SELECT investory.run_system_audit(?, 'POST_IMPORT')", UUID.class, 1L))
        .thenThrow(new IllegalStateException("audit timeout"));

    assertDoesNotThrow(
        () ->
            new ReconciliationRefreshService(portfolioProjectionService, jdbcTemplate, true)
                .refreshAfterImport(1L));

    verify(jdbcTemplate)
        .queryForObject("SELECT investory.run_system_audit(?, 'POST_IMPORT')", UUID.class, 1L);
    verify(portfolioProjectionService).refreshReconciliationViews();
  }

  @Test
  void disabledRefreshDoesNotAuditStaleReconciliation() {
    new ReconciliationRefreshService(portfolioProjectionService, jdbcTemplate, false)
        .refreshAfterImport(1L);

    verifyNoInteractions(portfolioProjectionService, jdbcTemplate);
  }

  @Test
  void failedRefreshDoesNotAuditPotentiallyStaleFinancialReconciliation() {
    doThrow(new IllegalStateException("reconciliation refresh timeout"))
        .when(portfolioProjectionService)
        .refreshReconciliationViews();

    new ReconciliationRefreshService(portfolioProjectionService, jdbcTemplate, true)
        .refreshAfterImport(1L);

    verify(portfolioProjectionService).refreshReconciliationViews();
    verifyNoInteractions(jdbcTemplate);
  }
}
