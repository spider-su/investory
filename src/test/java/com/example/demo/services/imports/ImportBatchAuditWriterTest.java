package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;
import com.example.demo.data.repository.ImportBatch;
import com.example.demo.data.repository.ImportBatchRepository;
import com.example.demo.data.repository.ImportRowError;
import com.example.demo.data.repository.ImportRowErrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportBatchAuditWriterTest {

    @Mock
    private ImportBatchRepository importBatchRepository;
    @Mock
    private ImportRowErrorRepository importRowErrorRepository;

    @InjectMocks
    private ImportBatchAuditWriter auditWriter;

    @Test
    void startBatch_persistsReceivedRowWithMetadata() {
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        ImportBatch batch = auditWriter.startBatch(BrokerType.IBKR, ImportSourceType.MANUAL,
                "ref", "ibkr.csv", "abc");

        assertEquals(101L, batch.getId());
        assertEquals(BrokerType.IBKR, batch.getBroker());
        assertEquals(ImportSourceType.MANUAL, batch.getSourceType());
        assertEquals("ref", batch.getSourceRef());
        assertEquals("ibkr.csv", batch.getFileName());
        assertEquals("abc", batch.getFileSha256());
        assertEquals(ImportBatchStatus.RECEIVED, batch.getStatus());
        assertNotNull(batch.getStartedAt());
        assertEquals(0, batch.getRowsTotal());
    }

    @Test
    void finalizeApplied_updatesCountsAndStatus() {
        ImportBatch existing = new ImportBatch();
        existing.setId(5L);
        existing.setStatus(ImportBatchStatus.RECEIVED);
        when(importBatchRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImportBatch result = auditWriter.finalizeApplied(5L, new ImportExecutionResult(10, 9, 1, "done"));

        assertEquals(ImportBatchStatus.APPLIED, result.getStatus());
        assertEquals(10, result.getRowsTotal());
        assertEquals(9, result.getRowsApplied());
        assertEquals(1, result.getRowsFailed());
        assertEquals("done", result.getErrorMessage());
        assertNotNull(result.getFinishedAt());
    }

    @Test
    void finalizeFailed_persistsErrorAndTruncatesRawPayload() {
        ImportBatch existing = new ImportBatch();
        existing.setId(7L);
        when(importBatchRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] big = new byte[ImportBatchAuditWriter.RAW_PAYLOAD_LIMIT + 1024];
        Arrays.fill(big, (byte) 'a');

        ImportBatch failed = auditWriter.finalizeFailed(7L, "boom", big);

        assertEquals(ImportBatchStatus.FAILED, failed.getStatus());
        assertEquals("boom", failed.getErrorMessage());
        assertEquals(1, failed.getRowsFailed());

        ArgumentCaptor<ImportRowError> errorCaptor = ArgumentCaptor.forClass(ImportRowError.class);
        verify(importRowErrorRepository).save(errorCaptor.capture());
        ImportRowError saved = errorCaptor.getValue();
        assertEquals("IMPORT_FAILED", saved.getErrorCode());
        assertEquals("boom", saved.getErrorMessage());
        assertNotNull(saved.getRawPayload());
        assertTrue(saved.getRawPayload().startsWith("[truncated to "),
                "expected truncation header, got: " + saved.getRawPayload().substring(0, Math.min(64, saved.getRawPayload().length())));
    }

    @Test
    void finalizeFailed_keepsShortPayloadInline() {
        ImportBatch existing = new ImportBatch();
        existing.setId(8L);
        when(importBatchRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditWriter.finalizeFailed(8L, "boom", "hello".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<ImportRowError> errorCaptor = ArgumentCaptor.forClass(ImportRowError.class);
        verify(importRowErrorRepository).save(errorCaptor.capture());
        assertEquals("hello", errorCaptor.getValue().getRawPayload());
    }

    @Test
    void truncate_isNullSafe() {
        assertNull(ImportBatchAuditWriter.truncate(null));
        assertNull(ImportBatchAuditWriter.truncate(new byte[0]));
    }

    @Test
    void finalizeApplied_throwsWhenBatchIsMissing() {
        when(importBatchRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> auditWriter.finalizeApplied(404L,
                new ImportExecutionResult(0, 0, 0, "")));
    }
}

