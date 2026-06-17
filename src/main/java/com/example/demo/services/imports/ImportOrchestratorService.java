package com.example.demo.services.imports;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.infrastructure.ImportSourceType;
import com.example.demo.infrastructure.repository.ImportBatch;
import com.example.demo.infrastructure.repository.ImportBatchRepository;
import com.example.demo.infrastructure.repository.ImportRowErrorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Coordinates broker imports: dedup -> persist a RECEIVED batch -> run the parser
 * -> persist the APPLIED or FAILED outcome (with the failing row's payload truncated).
 *
 * <p>The orchestrator deliberately is NOT {@code @Transactional}; audit writes go through
 * {@link ImportBatchAuditWriter} which uses {@code REQUIRES_NEW} so that a parser-side
 * rollback does not erase the FAILED batch + {@code import_row_error} rows.
 */
@Slf4j
@Service
public class ImportOrchestratorService {

    private final Map<BrokerType, BrokerImportParser> parserByBroker;
    private final ImportBatchRepository importBatchRepository;
    private final ImportRowErrorRepository importRowErrorRepository;
    private final ImportBatchAuditWriter auditWriter;

    public ImportOrchestratorService(List<BrokerImportParser> parsers,
                                     ImportBatchRepository importBatchRepository,
                                     ImportRowErrorRepository importRowErrorRepository,
                                     ImportBatchAuditWriter auditWriter) {
        this.parserByBroker = new EnumMap<>(BrokerType.class);
        for (BrokerImportParser parser : parsers) {
            BrokerImportParser previous = this.parserByBroker.put(parser.brokerType(), parser);
            if (previous != null) {
                throw new IllegalStateException("Duplicate BrokerImportParser registered for "
                        + parser.brokerType() + ": " + previous + " and " + parser);
            }
        }
        this.importBatchRepository = importBatchRepository;
        this.importRowErrorRepository = importRowErrorRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public Optional<ImportBatchDetailsResponse> getBatch(Long batchId) {
        return importBatchRepository.findById(batchId).map(this::toDetailsResponse);
    }

    @Transactional(readOnly = true)
    public Optional<ImportBatchDetailsResponse> getLatestBatch() {
        return importBatchRepository.findFirstByOrderByIdDesc().map(this::toDetailsResponse);
    }

    @Transactional(readOnly = true)
    public List<ImportBatchDetailsResponse> listBatches(int limit) {
        return importBatchRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(limit)
                .map(this::toDetailsResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ImportRowErrorResponse> getBatchErrors(Long batchId) {
        return importRowErrorRepository.findAllByBatch_IdOrderByIdAsc(batchId).stream()
                .map(error -> new ImportRowErrorResponse(
                        error.getId(),
                        error.getSheetName(),
                        error.getRowNumber(),
                        error.getErrorCode(),
                        error.getErrorMessage()
                ))
                .collect(Collectors.toList());
    }

    public ImportBatchResponse importFile(BrokerType broker,
                                          byte[] fileBytes,
                                          String fileName,
                                          ImportSourceType sourceType,
                                          String sourceRef) {
        BrokerImportParser parser = parserByBroker.get(broker);
        if (parser == null) {
            throw new IllegalArgumentException("No parser registered for broker: " + broker);
        }

        String checksum = sha256(fileBytes);
        Optional<ImportBatch> existing = auditWriter.findExistingAppliedBatch(broker, checksum);
        if (existing.isPresent()) {
            // Duplicate is a per-request observation; do NOT mutate the original successful
            // batch's row in the database (used to overwrite errorMessage and poison the
            // details endpoint forever after).
            ImportBatch batch = existing.get();
            return toBatchResponse(batch, "File already imported, returning existing batch", true);
        }

        ImportBatch batch = auditWriter.startBatch(broker, sourceType, sourceRef, fileName, checksum);

        ImportExecutionResult result;
        try {
            result = parser.importFile(new ByteArrayInputStream(fileBytes), fileName);
        } catch (Exception e) {
            log.warn("Broker import failed for {} ({} bytes): {}", broker, fileBytes.length, e.getMessage());
            ImportBatch failed = auditWriter.finalizeFailed(batch.getId(), e.getMessage(), fileBytes);
            throw new ImportFailedException("Failed to import file for broker " + broker
                    + " (batchId=" + failed.getId() + "): " + e.getMessage(), e);
        }

        ImportBatch finalized = auditWriter.finalizeApplied(batch.getId(), result);
        return toBatchResponse(finalized, finalized.getErrorMessage(), false);
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute file checksum", e);
        }
    }

    private ImportBatchDetailsResponse toDetailsResponse(ImportBatch batch) {
        return new ImportBatchDetailsResponse(
                batch.getId(),
                batch.getBroker(),
                batch.getSourceType(),
                batch.getSourceRef(),
                batch.getFileName(),
                batch.getFileSha256(),
                batch.getStatus(),
                nz(batch.getRowsTotal()),
                nz(batch.getRowsApplied()),
                nz(batch.getRowsFailed()),
                batch.getErrorMessage(),
                // "duplicate" is per-upload semantics; not meaningful when looking up a batch by id.
                false,
                batch.getStartedAt(),
                batch.getFinishedAt()
        );
    }

    private ImportBatchResponse toBatchResponse(ImportBatch batch, String message, boolean duplicate) {
        return new ImportBatchResponse(
                batch.getId(),
                batch.getBroker(),
                batch.getStatus(),
                nz(batch.getRowsTotal()),
                nz(batch.getRowsApplied()),
                nz(batch.getRowsFailed()),
                message,
                duplicate
        );
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}

