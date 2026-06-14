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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportOrchestratorService {

    private final List<BrokerImportParser> parsers;
    private final ImportBatchRepository importBatchRepository;
    private final ImportRowErrorRepository importRowErrorRepository;

    @Transactional
    public ImportBatchResponse importFile(BrokerType broker, byte[] fileBytes, String fileName, ImportSourceType sourceType, String sourceRef) {
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
        batch.setFileSha256(sha256(fileBytes));
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

            return new ImportBatchResponse(batch.getId(), broker, batch.getStatus(),
                    batch.getRowsTotal(), batch.getRowsApplied(), batch.getRowsFailed(), batch.getErrorMessage());
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
}

