package com.example.demo.services.imports;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.infrastructure.ImportSourceType;
import com.example.demo.infrastructure.repository.imports.ImportHistory;
import com.example.demo.services.AssetPriceFallbackService;
import com.example.demo.services.PortfolioProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Coordinates broker imports: dedup -> persist a STARTED batch -> run the parser
 * -> persist the COMPLETED or FAILED outcome (with the failing row's payload truncated).
 *
 * <p>The orchestrator deliberately is not transactional; audit writes go through
 * {@link ImportBatchAuditWriter} which uses {@code REQUIRES_NEW} so that a parser-side
 * rollback does not erase the FAILED batch + {@code import_row_error} rows.
 */
@Slf4j
@Service
public class ImportOrchestratorService {

    private final Map<BrokerType, BrokerImportParser> parserByBroker;
    private final ImportBatchAuditWriter auditWriter;
    private final AssetPriceFallbackService assetPriceFallbackService;
    private final PortfolioProjectionService portfolioProjectionService;

    public ImportOrchestratorService(List<BrokerImportParser> parsers,
                                     ImportBatchAuditWriter auditWriter,
                                     AssetPriceFallbackService assetPriceFallbackService,
                                      PortfolioProjectionService portfolioProjectionService) {
        this.parserByBroker = new EnumMap<>(BrokerType.class);
        for (BrokerImportParser parser : parsers) {
            BrokerImportParser previous = this.parserByBroker.put(parser.brokerType(), parser);
            if (previous != null) {
                throw new IllegalStateException("Duplicate BrokerImportParser registered for "
                        + parser.brokerType() + ": " + previous + " and " + parser);
            }
        }
        this.auditWriter = auditWriter;
        this.assetPriceFallbackService = assetPriceFallbackService;
        this.portfolioProjectionService = portfolioProjectionService;
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
        Optional<ImportHistory> existing = auditWriter.findExistingAppliedBatch(broker, checksum);
        if (existing.isPresent()) {
            if (shouldReprocessDuplicate(broker)) {
                ImportHistory batch = existing.get();
                reprocessDuplicate(parser, fileBytes, fileName, batch);
                return toBatchResponse(batch, "Duplicate " + broker + " file reprocessed to rebuild open positions", false);
            }
            // Duplicate is a per-request observation; do NOT mutate the original successful
            // batch's row in the database (used to overwrite errorMessage and poison the
            // details endpoint forever after).
            ImportHistory batch = existing.get();
            return toBatchResponse(batch, "File already imported, returning existing batch", true);
        }

        ImportHistory batch = auditWriter.startBatch(broker, sourceType, sourceRef, fileName, checksum);

        ImportExecutionResult result;
        try {
            result = parser.importFile(new ByteArrayInputStream(fileBytes), fileName);
        } catch (Exception e) {
            String errorMessage = exceptionMessage(e);
            log.warn("Broker import failed for {} ({} bytes): {}", broker, fileBytes.length, errorMessage, e);
            ImportHistory failed = auditWriter.finalizeFailed(batch.getId(), errorMessage, fileBytes);
            throw new ImportFailedException("Failed to import file for broker " + broker
                    + " (batchId=" + failed.getId() + "): " + errorMessage, e);
        }

        ImportHistory finalized = auditWriter.finalizeApplied(batch.getId(), result);

        refreshDerivedData(finalized);

        return toBatchResponse(finalized, finalized.getErrorMessage(), false);
    }

    private boolean shouldReprocessDuplicate(BrokerType broker) {
        return broker == BrokerType.IBKR || broker == BrokerType.XTB;
    }

    private void reprocessDuplicate(
            BrokerImportParser parser, byte[] fileBytes, String fileName, ImportHistory batch) {
        try {
            parser.importFile(new ByteArrayInputStream(fileBytes), fileName);
            refreshDerivedData(batch);
        } catch (Exception e) {
            String errorMessage = exceptionMessage(e);
            log.warn("Duplicate import repair failed for batchId={}: {}", batch.getId(), errorMessage, e);
            throw new ImportFailedException("Failed to reprocess duplicate import for broker "
                    + batch.getBroker() + " (batchId=" + batch.getId() + "): " + errorMessage, e);
        }
    }

    private static String exceptionMessage(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    private void refreshDerivedData(ImportHistory batch) {
        try {
            assetPriceFallbackService.populateMissingPricesFromOpenPositions();
        } catch (Exception e) {
            log.warn("Asset price fallback population failed after import (batchId={}): {}",
                    batch.getId(), e.getMessage());
        }

        try {
            portfolioProjectionService.recalculateAll();
        } catch (Exception e) {
            log.warn("Projection recalculation failed after import (batchId={}): {}",
                    batch.getId(), e.getMessage());
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

    private ImportBatchResponse toBatchResponse(ImportHistory batch, String message, boolean duplicate) {
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

