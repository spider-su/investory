package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;
import com.example.demo.data.repository.ImportBatch;
import com.example.demo.data.repository.ImportBatchRepository;
import com.example.demo.data.repository.ImportRowErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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

@ExtendWith(MockitoExtension.class)
class ImportOrchestratorServiceTest {

    @Mock
    private BrokerImportParser xtbParser;
    @Mock
    private ImportBatchRepository importBatchRepository;
    @Mock
    private ImportRowErrorRepository importRowErrorRepository;
    @Mock
    private ImportBatchAuditWriter auditWriter;

    private ImportOrchestratorService importOrchestratorService;

    @BeforeEach
    void setUp() {
        when(xtbParser.brokerType()).thenReturn(BrokerType.XTB);
        importOrchestratorService = new ImportOrchestratorService(
                List.of(xtbParser),
                importBatchRepository,
                importRowErrorRepository,
                auditWriter
        );
    }

    @Test
    void constructor_rejectsDuplicateParsersForSameBroker() {
        BrokerImportParser other = org.mockito.Mockito.mock(BrokerImportParser.class);
        when(other.brokerType()).thenReturn(BrokerType.XTB);

        assertThrows(IllegalStateException.class, () -> new ImportOrchestratorService(
                List.of(xtbParser, other),
                importBatchRepository,
                importRowErrorRepository,
                auditWriter));
    }

    @Test
    void importFile_returnsDuplicateResponseWithoutMutatingExistingBatch() throws Exception {
        ImportBatch existing = batch(77L, ImportBatchStatus.APPLIED, "ok", 12, 12, 0);
        when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
                .thenReturn(Optional.of(existing));

        ImportBatchResponse response = importOrchestratorService.importFile(
                BrokerType.XTB,
                "abc".getBytes(StandardCharsets.UTF_8),
                "file.xlsx",
                ImportSourceType.MANUAL,
                null
        );

        assertEquals(77L, response.batchId());
        assertTrue(response.duplicate());
        // Crucial: must NOT touch the existing batch (used to overwrite errorMessage).
        verify(auditWriter, never()).startBatch(any(), any(), any(), anyString(), anyString());
        verify(auditWriter, never()).finalizeApplied(any(), any());
        verify(xtbParser, never()).importFile(any(), anyString());
        assertEquals("ok", existing.getErrorMessage(), "existing batch must not be mutated");
    }

    @Test
    void importFile_processesNewFileAndReturnsAppliedSummary() throws Exception {
        when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
                .thenReturn(Optional.empty());
        ImportBatch received = batch(1L, ImportBatchStatus.RECEIVED, null, 0, 0, 0);
        when(auditWriter.startBatch(eq(BrokerType.XTB), eq(ImportSourceType.MANUAL), any(),
                eq("file.xlsx"), anyString())).thenReturn(received);
        ImportExecutionResult parserResult = new ImportExecutionResult(10, 9, 1, "ok");
        when(xtbParser.importFile(any(), eq("file.xlsx"))).thenReturn(parserResult);
        ImportBatch applied = batch(1L, ImportBatchStatus.APPLIED, "ok", 10, 9, 1);
        when(auditWriter.finalizeApplied(1L, parserResult)).thenReturn(applied);

        ImportBatchResponse response = importOrchestratorService.importFile(
                BrokerType.XTB,
                "new-file".getBytes(StandardCharsets.UTF_8),
                "file.xlsx",
                ImportSourceType.MANUAL,
                null
        );

        assertEquals(1L, response.batchId());
        assertFalse(response.duplicate());
        assertEquals(10, response.rowsTotal());
        assertEquals(9, response.rowsApplied());
        assertEquals(1, response.rowsFailed());
        assertEquals("ok", response.message());
        verify(xtbParser, times(1)).importFile(any(), eq("file.xlsx"));
    }

    @Test
    void importFile_recordsFailedBatchAndRowErrorWhenParserThrows() throws Exception {
        when(auditWriter.findExistingAppliedBatch(eq(BrokerType.XTB), anyString()))
                .thenReturn(Optional.empty());
        ImportBatch received = batch(2L, ImportBatchStatus.RECEIVED, null, 0, 0, 0);
        when(auditWriter.startBatch(any(), any(), any(), anyString(), anyString())).thenReturn(received);
        when(xtbParser.importFile(any(), anyString())).thenThrow(new IllegalStateException("boom"));
        when(auditWriter.finalizeFailed(eq(2L), eq("boom"), any()))
                .thenReturn(batch(2L, ImportBatchStatus.FAILED, "boom", 0, 0, 1));

        byte[] payload = "bad-bytes".getBytes(StandardCharsets.UTF_8);
        ImportFailedException ex = assertThrows(ImportFailedException.class, () ->
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
        assertThrows(IllegalArgumentException.class, () -> importOrchestratorService.importFile(
                BrokerType.IBKR,
                "x".getBytes(StandardCharsets.UTF_8),
                "file.csv",
                ImportSourceType.MANUAL,
                null));
        verify(auditWriter, never()).startBatch(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void getLatestBatch_returnsLatestBatchDetails() {
        ImportBatch latest = batch(13L, ImportBatchStatus.APPLIED, "ok", 5, 5, 0);
        latest.setBroker(BrokerType.XTB);
        latest.setSourceType(ImportSourceType.MANUAL);
        latest.setFileName("latest.xlsx");
        latest.setFileSha256("hash");
        latest.setStartedAt(ZonedDateTime.now());

        when(importBatchRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(latest));

        Optional<ImportBatchDetailsResponse> details = importOrchestratorService.getLatestBatch();

        assertTrue(details.isPresent());
        assertEquals(13L, details.get().batchId());
        // Details endpoint no longer infers "duplicate" from a persisted message; always false.
        assertFalse(details.get().duplicate());
    }

    @Test
    void getBatch_returnsEmptyWhenMissing() {
        when(importBatchRepository.findById(404L)).thenReturn(Optional.empty());
        assertTrue(importOrchestratorService.getBatch(404L).isEmpty());
    }

    private static ImportBatch batch(Long id, ImportBatchStatus status, String message,
                                     int total, int applied, int failed) {
        ImportBatch b = new ImportBatch();
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
