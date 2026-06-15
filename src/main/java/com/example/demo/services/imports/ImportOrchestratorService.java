package com.example.demo.services.imports;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;
import com.example.demo.data.repository.ImportBatch;
import com.example.demo.data.repository.ImportBatchRepository;
import com.example.demo.data.repository.ImportRowError;
import com.example.demo.data.repository.ImportRowErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportOrchestratorService {

    private final List<BrokerImportParser> parsers;
    private final ImportBatchRepository importBatchRepository;
    private final ImportRowErrorRepository importRowErrorRepository;

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

    @Transactional
    public ImportBatchResponse importFile(BrokerType broker, byte[] fileBytes, String fileName, ImportSourceType sourceType, String sourceRef) {
        String checksum = sha256(fileBytes);
        Optional<ImportBatch> existingBatch = importBatchRepository.findFirstByBrokerAndFileSha256OrderByIdDesc(broker, checksum)
                .filter(batch -> batch.getStatus() == ImportBatchStatus.APPLIED);
        if (existingBatch.isPresent()) {
            ImportBatch batch = existingBatch.get();
            batch.setErrorMessage("File already imported, returning existing batch");
            importBatchRepository.save(batch);
            return toBatchResponse(batch, "File already imported, returning existing batch", true);
        }

        Map<BrokerType, BrokerImportParser> parserByBroker = parsers.stream()
                .collect(Collectors.toMap(BrokerImportParser::brokerType, Function.identity()));

        BrokerImportParser parser = parserByBroker.get(broker);
        if (parser == null) {
            throw new IllegalArgumentException("No parser registered for broker: " + broker);
        }

        ImportBatch batch = new ImportBatch();
        batch.setBroker(broker);
        batch.setSourceType(sourceType);
        batch.setSourceRef(sourceRef);
        batch.setFileName(fileName);
        batch.setFileSha256(checksum);
        batch.setStartedAt(ZonedDateTime.now());
        batch.setStatus(ImportBatchStatus.RECEIVED);
        batch.setRowsTotal(0);
        batch.setRowsApplied(0);
        batch.setRowsFailed(0);
        batch = importBatchRepository.save(batch);

        try {
            ImportExecutionResult result = parser.importFile(new ByteArrayInputStream(fileBytes), fileName);
            batch.setStatus(ImportBatchStatus.APPLIED);
            batch.setRowsTotal(result.rowsTotal());
            batch.setRowsApplied(result.rowsApplied());
            batch.setRowsFailed(result.rowsFailed());
            batch.setErrorMessage(result.details());
            batch.setFinishedAt(ZonedDateTime.now());
            importBatchRepository.save(batch);

            return toBatchResponse(batch, batch.getErrorMessage(), false);
        } catch (Exception e) {
            batch.setStatus(ImportBatchStatus.FAILED);
            batch.setRowsFailed(1);
            batch.setErrorMessage(e.getMessage());
            batch.setFinishedAt(ZonedDateTime.now());
            importBatchRepository.save(batch);

            ImportRowError rowError = new ImportRowError();
            rowError.setBatch(batch);
            rowError.setErrorCode("IMPORT_FAILED");
            rowError.setErrorMessage(e.getMessage());
            rowError.setRawPayload(new String(fileBytes, StandardCharsets.UTF_8));
            importRowErrorRepository.save(rowError);
            throw new RuntimeException("Failed to import file for broker " + broker, e);
        }
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
                batch.getRowsTotal() != null ? batch.getRowsTotal() : 0,
                batch.getRowsApplied() != null ? batch.getRowsApplied() : 0,
                batch.getRowsFailed() != null ? batch.getRowsFailed() : 0,
                batch.getErrorMessage(),
                isDuplicateBatch(batch),
                batch.getStartedAt(),
                batch.getFinishedAt()
        );
    }

    private ImportBatchResponse toBatchResponse(ImportBatch batch, String message, boolean duplicate) {
        return new ImportBatchResponse(
                batch.getId(),
                batch.getBroker(),
                batch.getStatus(),
                batch.getRowsTotal() != null ? batch.getRowsTotal() : 0,
                batch.getRowsApplied() != null ? batch.getRowsApplied() : 0,
                batch.getRowsFailed() != null ? batch.getRowsFailed() : 0,
                message,
                duplicate
        );
    }

    private boolean isDuplicateBatch(ImportBatch batch) {
        return batch.getErrorMessage() != null && batch.getErrorMessage().startsWith("File already imported");
    }
}

