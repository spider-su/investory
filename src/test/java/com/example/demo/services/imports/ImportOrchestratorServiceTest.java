package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;
import com.example.demo.data.repository.ImportBatch;
import com.example.demo.data.repository.ImportBatchRepository;
import com.example.demo.data.repository.ImportRowErrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportOrchestratorServiceTest {

    @Mock
    private BrokerImportParser xtbParser;
    @Mock
    private ImportBatchRepository importBatchRepository;
    @Mock
    private ImportRowErrorRepository importRowErrorRepository;

    private ImportOrchestratorService importOrchestratorService;

    @BeforeEach
    void setUp() {
        importOrchestratorService = new ImportOrchestratorService(
                List.of(xtbParser),
                importBatchRepository,
                importRowErrorRepository
        );
    }

    @Test
    void importFile_returnsDuplicateResponseWhenAppliedBatchExists() throws Exception {
        ImportBatch existing = new ImportBatch();
        existing.setId(77L);
        existing.setBroker(BrokerType.XTB);
        existing.setStatus(ImportBatchStatus.APPLIED);
        existing.setRowsTotal(12);
        existing.setRowsApplied(12);
        existing.setRowsFailed(0);

        when(importBatchRepository.findFirstByBrokerAndFileSha256OrderByIdDesc(eq(BrokerType.XTB), anyString()))
                .thenReturn(Optional.of(existing));
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImportBatchResponse response = importOrchestratorService.importFile(
                BrokerType.XTB,
                "abc".getBytes(StandardCharsets.UTF_8),
                "file.xlsx",
                ImportSourceType.MANUAL,
                null
        );

        assertEquals(77L, response.batchId());
        assertTrue(response.duplicate());
        verify(xtbParser, never()).importFile(any(), anyString());
    }

    @Test
    void importFile_processesNewFileAndReturnsNonDuplicate() throws Exception {
        when(importBatchRepository.findFirstByBrokerAndFileSha256OrderByIdDesc(eq(BrokerType.XTB), anyString()))
                .thenReturn(Optional.empty());
        when(xtbParser.brokerType()).thenReturn(BrokerType.XTB);
        when(xtbParser.importFile(any(), anyString())).thenReturn(new ImportExecutionResult(10, 9, 1, "ok"));
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(1L);
            }
            if (batch.getStartedAt() == null) {
                batch.setStartedAt(ZonedDateTime.now());
            }
            return batch;
        });

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
        verify(xtbParser, times(1)).importFile(any(), eq("file.xlsx"));
    }

    @Test
    void getLatestBatch_returnsLatestBatchDetails() {
        ImportBatch latest = new ImportBatch();
        latest.setId(13L);
        latest.setBroker(BrokerType.XTB);
        latest.setSourceType(ImportSourceType.MANUAL);
        latest.setFileName("latest.xlsx");
        latest.setFileSha256("hash");
        latest.setStatus(ImportBatchStatus.APPLIED);
        latest.setRowsTotal(5);
        latest.setRowsApplied(5);
        latest.setRowsFailed(0);
        latest.setErrorMessage("ok");
        latest.setStartedAt(ZonedDateTime.now());

        when(importBatchRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(latest));

        Optional<ImportBatchDetailsResponse> details = importOrchestratorService.getLatestBatch();

        assertTrue(details.isPresent());
        assertEquals(13L, details.get().batchId());
        assertFalse(details.get().duplicate());
    }
}



