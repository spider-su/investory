package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.accounting.PortfolioProjectionService;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.investment.market.price.AssetPriceFallbackService;
import com.smartbox.investory.investment.reconciliation.ReconciliationRefreshService;
import com.smartbox.investory.testsupport.portfolio.PortfolioScenarios;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportHistoryOrchestratorServiceTest {

  @Mock private BrokerImportParser xtbParser;
  @Mock private ImportBatchAuditWriter auditWriter;
  @Mock private AssetPriceFallbackService assetPriceFallbackService;
  @Mock private PortfolioProjectionService portfolioProjectionService;
  @Mock private ReconciliationRefreshService reconciliationRefreshService;
  private ImportOrchestratorService importOrchestratorService;

  @BeforeEach
  void setUp() {
    when(xtbParser.brokerType()).thenReturn(BrokerType.XTB);
    importOrchestratorService =
        new ImportOrchestratorService(
            List.of(xtbParser),
            auditWriter,
            assetPriceFallbackService,
            portfolioProjectionService,
            reconciliationRefreshService);
  }

  @Test
  void constructor_rejectsDuplicateParsersForSameBroker() {
    BrokerImportParser other = org.mockito.Mockito.mock(BrokerImportParser.class);
    when(other.brokerType()).thenReturn(BrokerType.XTB);

    assertThrows(
        IllegalStateException.class,
        () ->
            new ImportOrchestratorService(
                List.of(xtbParser, other),
                auditWriter,
                assetPriceFallbackService,
                portfolioProjectionService,
                reconciliationRefreshService));
  }

  @Test
  void importFile_reprocessesDuplicateIbkrFileWhenOpenPositionsAreMissing() throws Exception {
    BrokerImportParser ibkrParser = org.mockito.Mockito.mock(BrokerImportParser.class);
    when(ibkrParser.brokerType()).thenReturn(BrokerType.IBKR);
    importOrchestratorService =
        new ImportOrchestratorService(
            List.of(ibkrParser),
            auditWriter,
            assetPriceFallbackService,
            portfolioProjectionService,
            reconciliationRefreshService);
    PortfolioTestContext duplicateScenario = PortfolioScenarios.createDuplicateImportScenario();
    ImportHistoryEntity existing = duplicateScenario.imports().firstImport();
    ImportHistoryEntity reprocess = batch(88L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.IBKR), anyString()))
        .thenReturn(Optional.of(existing));
    when(auditWriter.startReprocessBatch(existing)).thenReturn(reprocess);
    ImportHistoryEntity applied = batch(88L, ImportBatchStatus.COMPLETED, "repaired", 1, 1, 0);
    when(auditWriter.finalizeApplied(88L, new ImportExecutionResult(1, 1, 0, "repaired")))
        .thenReturn(applied);
    when(ibkrParser.importFile(any(), eq("ibkr.csv")))
        .thenReturn(new ImportExecutionResult(1, 1, 0, "repaired"));

    ImportBatchResponse response =
        importOrchestratorService.importFile(
            BrokerType.IBKR,
            "same-file".getBytes(StandardCharsets.UTF_8),
            "ibkr.csv",
            ImportSourceType.MANUAL,
            null);

    assertFalse(response.duplicate());
    assertEquals(88L, response.batchId());
    verify(ibkrParser).importFile(any(), eq("ibkr.csv"));
    verify(assetPriceFallbackService).populateMissingPricesFromOpenPositions();
    verify(portfolioProjectionService).recalculateAll();
    verify(reconciliationRefreshService).refreshAfterImport(88L);
    verify(auditWriter, never()).startBatch(any(), any(), any(), anyString(), anyString());
  }

  @Test
  void importFile_reprocessesDuplicateXtbFileAndReturnsReloadedBatchState() throws Exception {
    ImportHistoryEntity existing = batch(77L, ImportBatchStatus.COMPLETED, "stale", 12, 12, 0);
    ImportHistoryEntity reprocess = batch(78L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    ImportHistoryEntity refreshed = batch(78L, ImportBatchStatus.COMPLETED, "refreshed", 12, 12, 0);
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.of(existing))
        .thenReturn(Optional.of(existing));
    when(auditWriter.startReprocessBatch(existing)).thenReturn(reprocess);
    ImportExecutionResult result = new ImportExecutionResult(12, 12, 0, "refreshed");
    when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(result);
    when(auditWriter.finalizeApplied(78L, result)).thenReturn(refreshed);

    ImportBatchResponse response =
        importOrchestratorService.importFile(
            BrokerType.XTB,
            "abc".getBytes(StandardCharsets.UTF_8),
            "file.xlsx",
            ImportSourceType.MANUAL,
            null);

    assertEquals(78L, response.batchId());
    assertFalse(response.duplicate());
    assertEquals(
        "Duplicate XTB file reprocessed as audit attempt 78 | refreshed", response.message());
    verify(auditWriter, never()).startBatch(any(), any(), any(), anyString(), anyString());
    verify(auditWriter).startReprocessBatch(existing);
    verify(auditWriter).finalizeApplied(78L, result);
    verify(xtbParser).importFile(any(), eq("file.xlsx"));
  }

  @Test
  void importFile_reprocessesCompletedAttemptAfterNewerFailedAttempt() throws Exception {
    ImportHistoryEntity completed = batch(77L, ImportBatchStatus.COMPLETED, "ok", 12, 12, 0);
    completed.setAttemptNo(1);
    ImportHistoryEntity failed = batch(78L, ImportBatchStatus.FAILED, "boom", 12, 0, 1);
    failed.setAttemptNo(2);
    ImportHistoryEntity reprocess = batch(79L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    reprocess.setAttemptNo(3);
    reprocess.setReprocessOf(completed.getId());
    ImportHistoryEntity applied = batch(79L, ImportBatchStatus.COMPLETED, "ok", 12, 12, 0);
    applied.setAttemptNo(3);
    applied.setReprocessOf(completed.getId());
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.of(completed));
    when(auditWriter.startReprocessBatch(completed)).thenReturn(reprocess);
    ImportExecutionResult result = new ImportExecutionResult(12, 12, 0, "ok");
    when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(result);
    when(auditWriter.finalizeApplied(79L, result)).thenReturn(applied);

    ImportBatchResponse response =
        importOrchestratorService.importFile(
            BrokerType.XTB,
            "same-file".getBytes(StandardCharsets.UTF_8),
            "file.xlsx",
            ImportSourceType.MANUAL,
            null);

    assertEquals(79L, response.batchId());
    assertEquals(ImportBatchStatus.COMPLETED, response.status());
    assertEquals(3, applied.getAttemptNo());
    assertEquals(ImportBatchStatus.COMPLETED, completed.getStatus());
    assertEquals(ImportBatchStatus.FAILED, failed.getStatus());
    verify(auditWriter).startReprocessBatch(completed);
    verify(auditWriter).finalizeApplied(79L, result);
  }

  @Test
  void importFile_reprocessesDuplicateXtbFileWithoutMutatingExistingBatch() throws Exception {
    ImportHistoryEntity existing = batch(77L, ImportBatchStatus.COMPLETED, "ok", 12, 12, 0);
    ImportHistoryEntity reprocess = batch(78L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.of(existing));
    when(auditWriter.startReprocessBatch(existing)).thenReturn(reprocess);
    ImportExecutionResult result = new ImportExecutionResult(12, 12, 0, "ok");
    when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(result);
    when(auditWriter.finalizeApplied(78L, result))
        .thenReturn(batch(78L, ImportBatchStatus.COMPLETED, "ok", 12, 12, 0));

    ImportBatchResponse response =
        importOrchestratorService.importFile(
            BrokerType.XTB,
            "abc".getBytes(StandardCharsets.UTF_8),
            "file.xlsx",
            ImportSourceType.MANUAL,
            null);

    assertEquals(78L, response.batchId());
    assertFalse(response.duplicate());
    // Crucial: the immutable original is not mutated; the repair gets its own attempt.
    verify(auditWriter, never()).startBatch(any(), any(), any(), anyString(), anyString());
    verify(auditWriter).finalizeApplied(78L, result);
    verify(xtbParser).importFile(any(), eq("file.xlsx"));
    verify(assetPriceFallbackService).populateMissingPricesFromOpenPositions();
    verify(portfolioProjectionService).recalculateAll();
    verify(reconciliationRefreshService).refreshAfterImport(78L);
    assertEquals("ok", existing.getErrorMessage(), "existing batch must not be mutated");
  }

  @Test
  void importFile_processesNewFileAndReturnsAppliedSummary() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistoryEntity received = batch(1L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    when(auditWriter.startBatch(
            eq(BrokerType.XTB), eq(ImportSourceType.MANUAL), any(), eq("file.xlsx"), anyString()))
        .thenReturn(received);
    ImportExecutionResult parserResult = new ImportExecutionResult(10, 9, 1, "ok");
    when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(parserResult);
    ImportHistoryEntity applied = batch(1L, ImportBatchStatus.COMPLETED, "ok", 10, 9, 1);
    when(auditWriter.finalizeApplied(1L, parserResult)).thenReturn(applied);

    ImportBatchResponse response =
        importOrchestratorService.importFile(
            BrokerType.XTB,
            "new-file".getBytes(StandardCharsets.UTF_8),
            "file.xlsx",
            ImportSourceType.MANUAL,
            null);

    assertEquals(1L, response.batchId());
    assertFalse(response.duplicate());
    assertEquals(10, response.rowsTotal());
    assertEquals(9, response.rowsApplied());
    assertEquals(1, response.rowsFailed());
    assertEquals("ok", response.message());
    verify(xtbParser, times(1)).importFile(any(), eq("file.xlsx"));
    verify(assetPriceFallbackService).populateMissingPricesFromOpenPositions();
    verify(portfolioProjectionService).recalculateAll();
    verify(reconciliationRefreshService).refreshAfterImport(1L);
  }

  @Test
  void importFile_changedWindowStartsNewBatchAndKeepsPriorImport() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistoryEntity started = batch(2L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    ImportExecutionResult result = new ImportExecutionResult(3, 3, 0, "changed window");
    ImportHistoryEntity applied = batch(2L, ImportBatchStatus.COMPLETED, "changed window", 3, 3, 0);
    when(auditWriter.startBatch(
            eq(BrokerType.XTB), eq(ImportSourceType.MANUAL), any(), eq("file.xlsx"), anyString()))
        .thenReturn(started);
    when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(result);
    when(auditWriter.finalizeApplied(2L, result)).thenReturn(applied);

    ImportBatchResponse response =
        importOrchestratorService.importFile(
            BrokerType.XTB,
            "partially-overlapping-window".getBytes(StandardCharsets.UTF_8),
            "file.xlsx",
            ImportSourceType.MANUAL,
            null);

    assertEquals(2L, response.batchId());
    assertFalse(response.duplicate());
    verify(auditWriter)
        .startBatch(
            eq(BrokerType.XTB), eq(ImportSourceType.MANUAL), any(), eq("file.xlsx"), anyString());
    verify(auditWriter).finalizeApplied(2L, result);
    verify(xtbParser).importFile(any(), eq("file.xlsx"));
  }

  @Test
  void importFile_rejectsZeroAppliedResultWithoutRefreshingDerivedData() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistoryEntity started = batch(95L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    ImportExecutionResult result = new ImportExecutionResult(10, 0, 10, "10 rejected rows");
    ImportHistoryEntity failed =
        batch(95L, ImportBatchStatus.FAILED, "10 rejected rows", 10, 0, 10);
    when(auditWriter.startBatch(any(), any(), any(), anyString(), anyString())).thenReturn(started);
    when(xtbParser.importFile(any(), anyString())).thenReturn(result);
    when(auditWriter.finalizeApplied(95L, result)).thenReturn(failed);

    ImportFailedException exception =
        assertThrows(
            ImportFailedException.class,
            () ->
                importOrchestratorService.importFile(
                    BrokerType.XTB,
                    "rejected".getBytes(),
                    "file.xlsx",
                    ImportSourceType.MANUAL,
                    null));

    assertTrue(exception.getMessage().contains("rejected"));
    verify(assetPriceFallbackService, never()).populateMissingPricesFromOpenPositions();
    verify(portfolioProjectionService, never()).recalculateAll();
    verify(reconciliationRefreshService, never()).refreshAfterImport(any());
  }

  @Test
  void importFile_retriesSameChecksumAfterFailedBatch() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistoryEntity recycled = batch(7L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    when(auditWriter.startBatch(
            eq(BrokerType.XTB), eq(ImportSourceType.MANUAL), any(), eq("file.xlsx"), anyString()))
        .thenReturn(recycled);
    ImportExecutionResult parserResult = new ImportExecutionResult(4, 4, 0, "retried ok");
    when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(parserResult);
    ImportHistoryEntity applied = batch(7L, ImportBatchStatus.COMPLETED, "retried ok", 4, 4, 0);
    when(auditWriter.finalizeApplied(7L, parserResult)).thenReturn(applied);

    ImportBatchResponse response =
        importOrchestratorService.importFile(
            BrokerType.XTB,
            "same-file".getBytes(StandardCharsets.UTF_8),
            "file.xlsx",
            ImportSourceType.MANUAL,
            null);

    assertEquals(7L, response.batchId());
    assertFalse(response.duplicate());
    assertEquals(ImportBatchStatus.COMPLETED, response.status());
    verify(auditWriter)
        .startBatch(
            eq(BrokerType.XTB), eq(ImportSourceType.MANUAL), any(), eq("file.xlsx"), anyString());
    verify(xtbParser).importFile(any(), eq("file.xlsx"));
    verify(auditWriter).finalizeApplied(7L, parserResult);
  }

  @Test
  void importFile_marksBatchNotReadyWhenProjectionRefreshFails() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistoryEntity started = batch(90L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    ImportExecutionResult result = new ImportExecutionResult(1, 1, 0, "imported");
    ImportHistoryEntity applied = batch(90L, ImportBatchStatus.COMPLETED, "imported", 1, 1, 0);
    ImportHistoryEntity notReady =
        batch(90L, ImportBatchStatus.NOT_READY, "projection failed", 1, 1, 0);
    when(auditWriter.startBatch(any(), any(), any(), anyString(), anyString())).thenReturn(started);
    when(xtbParser.importFile(any(), anyString())).thenReturn(result);
    when(auditWriter.finalizeApplied(90L, result)).thenReturn(applied);
    when(auditWriter.finalizeNotReady(eq(90L), eq(result), anyString())).thenReturn(notReady);
    org.mockito.Mockito.doThrow(new IllegalStateException("projection failed"))
        .when(portfolioProjectionService)
        .recalculateAll();

    ImportFailedException failure =
        assertThrows(
            ImportFailedException.class,
            () ->
                importOrchestratorService.importFile(
                    BrokerType.XTB,
                    "not-ready".getBytes(),
                    "file.xlsx",
                    ImportSourceType.MANUAL,
                    null));

    assertTrue(failure.getMessage().contains("not ready"));
    verify(auditWriter).finalizeNotReady(eq(90L), eq(result), contains("projection recalculation"));
  }

  @Test
  void importFile_returnsCompletedWhenReconciliationRefreshIsScheduled() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistoryEntity started = batch(91L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    ImportExecutionResult result = new ImportExecutionResult(1, 1, 0, "imported");
    when(auditWriter.startBatch(any(), any(), any(), anyString(), anyString())).thenReturn(started);
    when(xtbParser.importFile(any(), anyString())).thenReturn(result);
    when(auditWriter.finalizeApplied(91L, result))
        .thenReturn(batch(91L, ImportBatchStatus.COMPLETED, "imported", 1, 1, 0));
    ImportBatchResponse response =
        importOrchestratorService.importFile(
            BrokerType.XTB,
            "not-ready-reconciliation".getBytes(),
            "file.xlsx",
            ImportSourceType.MANUAL,
            null);

    assertEquals(ImportBatchStatus.COMPLETED, response.status());
    verify(reconciliationRefreshService).refreshAfterImport(91L);
    verify(auditWriter, never()).finalizeNotReady(eq(91L), eq(result), anyString());
  }

  @Test
  void duplicateReprocessAlsoBecomesNotReadyWhenDerivedRefreshFails() throws Exception {
    ImportHistoryEntity existing = batch(92L, ImportBatchStatus.COMPLETED, "old", 1, 1, 0);
    ImportHistoryEntity started = batch(93L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    ImportExecutionResult result = new ImportExecutionResult(1, 1, 0, "reprocessed");
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.of(existing));
    when(auditWriter.startReprocessBatch(existing)).thenReturn(started);
    when(xtbParser.importFile(any(), anyString())).thenReturn(result);
    when(auditWriter.finalizeApplied(93L, result))
        .thenReturn(batch(93L, ImportBatchStatus.COMPLETED, "reprocessed", 1, 1, 0));
    when(auditWriter.finalizeNotReady(eq(93L), eq(result), anyString()))
        .thenReturn(batch(93L, ImportBatchStatus.NOT_READY, "projection failed", 1, 1, 0));
    org.mockito.Mockito.doThrow(new IllegalStateException("projection failed"))
        .when(portfolioProjectionService)
        .recalculateAll();

    assertThrows(
        ImportFailedException.class,
        () ->
            importOrchestratorService.importFile(
                BrokerType.XTB,
                "duplicate-not-ready".getBytes(),
                "file.xlsx",
                ImportSourceType.MANUAL,
                null));
    verify(auditWriter).finalizeNotReady(eq(93L), eq(result), contains("projection recalculation"));
  }

  @Test
  void importFile_recordsFailedBatchAndRowErrorWhenParserThrows() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistoryEntity received = batch(2L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    when(auditWriter.startBatch(any(), any(), any(), anyString(), anyString()))
        .thenReturn(received);
    when(xtbParser.importFile(any(), anyString())).thenThrow(new IllegalStateException("boom"));
    when(auditWriter.finalizeFailed(eq(2L), eq("boom"), any()))
        .thenReturn(batch(2L, ImportBatchStatus.FAILED, "boom", 0, 0, 1));

    byte[] payload = "bad-bytes".getBytes(StandardCharsets.UTF_8);
    ImportFailedException ex =
        assertThrows(
            ImportFailedException.class,
            () ->
                importOrchestratorService.importFile(
                    BrokerType.XTB, payload, "broken.xlsx", ImportSourceType.MANUAL, null));
    assertTrue(ex.getMessage().contains("boom"));

    ArgumentCaptor<byte[]> rawCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(auditWriter).finalizeFailed(eq(2L), eq("boom"), rawCaptor.capture());
    assertEquals(payload.length, rawCaptor.getValue().length);
    verify(auditWriter, never()).finalizeApplied(any(), any());
  }

  @Test
  void importFile_rejectsUnknownBroker() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            importOrchestratorService.importFile(
                BrokerType.IBKR,
                "x".getBytes(StandardCharsets.UTF_8),
                "file.csv",
                ImportSourceType.MANUAL,
                null));
    verify(auditWriter, never()).startBatch(any(), any(), any(), anyString(), anyString());
  }

  private static ImportHistoryEntity batch(
      Long id, ImportBatchStatus status, String message, int total, int applied, int failed) {
    ImportHistoryEntity b = new ImportHistoryEntity();
    b.setId(id);
    b.setBroker(BrokerType.XTB);
    b.setStatus(status);
    b.setErrorMessage(message);
    b.setRowsTotal(total);
    b.setRowsApplied(applied);
    b.setRowsFailed(failed);
    return b;
  }
}
