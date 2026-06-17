package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;
import com.example.demo.data.repository.ImportBatch;
import com.example.demo.data.repository.ImportBatchRepository;
import com.example.demo.data.repository.ImportRowError;
import com.example.demo.data.repository.ImportRowErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Writes audit rows for {@link ImportOrchestratorService} in their own transactions.
 *
 * <p>Splitting the writes off the orchestrator solves a correctness bug: the orchestrator
 * used to be {@code @Transactional}, so when the parser failed and the catch block tried to
 * persist a {@code FAILED} batch + {@link ImportRowError}, Spring rolled the whole
 * transaction back and the audit trail was lost. Each method here runs in
 * {@link Propagation#REQUIRES_NEW}, so the audit rows survive even when the surrounding
 * parser transaction rolls back.
 */
@Component
@RequiredArgsConstructor
public class ImportBatchAuditWriter {

    /** Cap the raw payload we copy into {@code import_row_error.raw_payload}. */
    static final int RAW_PAYLOAD_LIMIT = 8 * 1024;

    private final ImportBatchRepository importBatchRepository;
    private final ImportRowErrorRepository importRowErrorRepository;

    @Transactional(readOnly = true)
    public Optional<ImportBatch> findExistingAppliedBatch(BrokerType broker, String sha256) {
        return importBatchRepository.findFirstByBrokerAndFileSha256OrderByIdDesc(broker, sha256)
                .filter(batch -> batch.getStatus() == ImportBatchStatus.APPLIED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch startBatch(BrokerType broker,
                                  ImportSourceType sourceType,
                                  String sourceRef,
                                  String fileName,
                                  String sha256) {
        ImportBatch batch = new ImportBatch();
        batch.setBroker(broker);
        batch.setSourceType(sourceType);
        batch.setSourceRef(sourceRef);
        batch.setFileName(fileName);
        batch.setFileSha256(sha256);
        batch.setStartedAt(ZonedDateTime.now());
        batch.setStatus(ImportBatchStatus.RECEIVED);
        batch.setRowsTotal(0);
        batch.setRowsApplied(0);
        batch.setRowsFailed(0);
        return importBatchRepository.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch finalizeApplied(Long batchId, ImportExecutionResult result) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Import batch missing: " + batchId));
        batch.setStatus(ImportBatchStatus.APPLIED);
        batch.setRowsTotal(result.rowsTotal());
        batch.setRowsApplied(result.rowsApplied());
        batch.setRowsFailed(result.rowsFailed());
        batch.setErrorMessage(result.details());
        batch.setFinishedAt(ZonedDateTime.now());
        return importBatchRepository.save(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch finalizeFailed(Long batchId, String message, byte[] rawPayload) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Import batch missing: " + batchId));
        batch.setStatus(ImportBatchStatus.FAILED);
        batch.setRowsFailed(1);
        batch.setErrorMessage(message);
        batch.setFinishedAt(ZonedDateTime.now());
        importBatchRepository.save(batch);

        ImportRowError rowError = new ImportRowError();
        rowError.setBatch(batch);
        rowError.setErrorCode("IMPORT_FAILED");
        rowError.setErrorMessage(message);
        rowError.setRawPayload(truncate(rawPayload));
        importRowErrorRepository.save(rowError);
        return batch;
    }

    static String truncate(byte[] rawPayload) {
        if (rawPayload == null || rawPayload.length == 0) {
            return null;
        }
        if (rawPayload.length <= RAW_PAYLOAD_LIMIT) {
            return new String(rawPayload, java.nio.charset.StandardCharsets.UTF_8);
        }
        byte[] head = new byte[RAW_PAYLOAD_LIMIT];
        System.arraycopy(rawPayload, 0, head, 0, RAW_PAYLOAD_LIMIT);
        return "[truncated to " + RAW_PAYLOAD_LIMIT + " of " + rawPayload.length + " bytes]\n"
                + new String(head, java.nio.charset.StandardCharsets.UTF_8);
    }
}

