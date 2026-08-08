package com.example.demo.services.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.infrastructure.ImportBatchStatus;
import com.example.demo.infrastructure.ImportSourceType;
import com.example.demo.infrastructure.repository.imports.ImportHistory;
import com.example.demo.services.AssetPriceFallbackService;
import com.example.demo.services.PortfolioProjectionService;
import com.example.demo.testsupport.portfolio.PortfolioScenarios;
import com.example.demo.testsupport.portfolio.PortfolioTestContext;
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
  private ImportOrchestratorService importOrchestratorService;

  @BeforeEach
  void setUp() {
    when(xtbParser.brokerType()).thenReturn(BrokerType.XTB);
    importOrchestratorService =
        new ImportOrchestratorService(
            List.of(xtbParser), auditWriter, assetPriceFallbackService, portfolioProjectionService);
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
                portfolioProjectionService));
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
            portfolioProjectionService);
    PortfolioTestContext duplicateScenario = PortfolioScenarios.createDuplicateImportScenario();
    ImportHistory existing = duplicateScenario.imports().firstImport();
    ImportHistory reprocess = batch(88L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.IBKR), anyString()))
        .thenReturn(Optional.of(existing));
    when(auditWriter.startReprocessBatch(existing)).thenReturn(reprocess);
    ImportHistory applied = batch(88L, ImportBatchStatus.COMPLETED, "repaired", 1, 1, 0);
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
    verify(auditWriter, never()).startBatch(any(), any(), any(), anyString(), anyString());
  }

  @Test
  void importFile_reprocessesDuplicateXtbFileAndReturnsReloadedBatchState() throws Exception {
    ImportHistory existing = batch(77L, ImportBatchStatus.COMPLETED, "stale", 12, 12, 0);
    ImportHistory reprocess = batch(78L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    ImportHistory refreshed = batch(78L, ImportBatchStatus.COMPLETED, "refreshed", 12, 12, 0);
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
    ImportHistory completed = batch(77L, ImportBatchStatus.COMPLETED, "ok", 12, 12, 0);
    completed.setAttemptNo(1);
    ImportHistory failed = batch(78L, ImportBatchStatus.FAILED, "boom", 12, 0, 1);
    failed.setAttemptNo(2);
    ImportHistory reprocess = batch(79L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    reprocess.setAttemptNo(3);
    reprocess.setReprocessOf(completed.getId());
    ImportHistory applied = batch(79L, ImportBatchStatus.COMPLETED, "ok", 12, 12, 0);
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
    ImportHistory existing = batch(77L, ImportBatchStatus.COMPLETED, "ok", 12, 12, 0);
    ImportHistory reprocess = batch(78L, ImportBatchStatus.STARTED, null, 0, 0, 0);
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
    assertEquals("ok", existing.getErrorMessage(), "existing batch must not be mutated");
  }

  @Test
  void importFile_processesNewFileAndReturnsAppliedSummary() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistory received = batch(1L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    when(auditWriter.startBatch(
            eq(BrokerType.XTB), eq(ImportSourceType.MANUAL), any(), eq("file.xlsx"), anyString()))
        .thenReturn(received);
    ImportExecutionResult parserResult = new ImportExecutionResult(10, 9, 1, "ok");
    when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(parserResult);
    ImportHistory applied = batch(1L, ImportBatchStatus.COMPLETED, "ok", 10, 9, 1);
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
  }

  @Test
  void importFile_retriesSameChecksumAfterFailedBatch() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistory recycled = batch(7L, ImportBatchStatus.STARTED, null, 0, 0, 0);
    when(auditWriter.startBatch(
            eq(BrokerType.XTB), eq(ImportSourceType.MANUAL), any(), eq("file.xlsx"), anyString()))
        .thenReturn(recycled);
    ImportExecutionResult parserResult = new ImportExecutionResult(4, 4, 0, "retried ok");
    when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(parserResult);
    ImportHistory applied = batch(7L, ImportBatchStatus.COMPLETED, "retried ok", 4, 4, 0);
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
  void importFile_recordsFailedBatchAndRowErrorWhenParserThrows() throws Exception {
    when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
        .thenReturn(Optional.empty());
    ImportHistory received = batch(2L, ImportBatchStatus.STARTED, null, 0, 0, 0);
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

  private static ImportHistory batch(
      Long id, ImportBatchStatus status, String message, int total, int applied, int failed) {
    ImportHistory b = new ImportHistory();
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
