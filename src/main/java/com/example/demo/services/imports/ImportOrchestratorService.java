package com.example.demo.services.imports;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.infrastructure.ImportSourceType;
import com.example.demo.infrastructure.repository.imports.ImportHistory;
import com.example.demo.services.AssetPriceFallbackService;
import com.example.demo.services.PortfolioProjectionService;
import com.example.demo.services.ReconciliationRefreshService;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinates broker imports: dedup -> persist a STARTED batch -> run the parser -> persist the
 * COMPLETED, PARTIAL, FAILED, or NOT_READY outcome.
 *
 * <p>The orchestrator deliberately is not transactional; audit writes go through {@link
 * ImportBatchAuditWriter} which uses {@code REQUIRES_NEW} so that a parser-side rollback does not
 * erase the FAILED batch audit record.
 */
@Slf4j
@Service
public class ImportOrchestratorService {

  private final Map<BrokerType, BrokerImportParser> parserByBroker;
  private final ImportBatchAuditWriter auditWriter;
  private final AssetPriceFallbackService assetPriceFallbackService;
  private final PortfolioProjectionService portfolioProjectionService;
  private final ReconciliationRefreshService reconciliationRefreshService;

  public ImportOrchestratorService(
      List<BrokerImportParser> parsers,
      ImportBatchAuditWriter auditWriter,
      AssetPriceFallbackService assetPriceFallbackService,
      PortfolioProjectionService portfolioProjectionService,
      ReconciliationRefreshService reconciliationRefreshService) {
    this.parserByBroker = new EnumMap<>(BrokerType.class);
    for (BrokerImportParser parser : parsers) {
      BrokerImportParser previous = this.parserByBroker.put(parser.brokerType(), parser);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate BrokerImportParser registered for "
                + parser.brokerType()
                + ": "
                + previous
                + " and "
                + parser);
      }
    }
    this.auditWriter = auditWriter;
    this.assetPriceFallbackService = assetPriceFallbackService;
    this.portfolioProjectionService = portfolioProjectionService;
    this.reconciliationRefreshService = reconciliationRefreshService;
  }

  public ImportBatchResponse importFile(
      BrokerType broker,
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
        ImportHistory original = existing.get();
        ImportHistory batch = auditWriter.startReprocessBatch(original);
        ImportHistory reloaded;
        try {
          ImportExecutionResult result =
              parser.importFile(new ByteArrayInputStream(fileBytes), fileName);
          reloaded = auditWriter.finalizeApplied(batch.getId(), result);
          throwIfFailed(reloaded);
          String refreshFailure = refreshDerivedData(reloaded);
          if (refreshFailure != null) {
            reloaded = auditWriter.finalizeNotReady(batch.getId(), result, refreshFailure);
            throw new ImportFailedException(
                "Duplicate import is not ready: " + refreshFailure, null);
          }
          return toBatchResponse(
              reloaded,
              combineMessage(
                  "Duplicate " + broker + " file reprocessed as audit attempt " + batch.getId(),
                  reloaded.getErrorMessage()),
              false);
        } catch (Exception e) {
          if (e instanceof ImportFailedException importFailedException) {
            throw importFailedException;
          }
          String errorMessage = exceptionMessage(e);
          log.warn("Duplicate import repair failed for batchId={}: {}", batch.getId(), errorMessage, e);
          ImportHistory failed = auditWriter.finalizeFailed(batch.getId(), errorMessage, fileBytes);
          throw new ImportFailedException(
              "Failed to reprocess duplicate import for broker "
                  + broker
                  + " (batchId="
                  + failed.getId()
                  + "): "
                  + errorMessage,
              e);
        }
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
      log.warn(
          "Broker import failed for {} ({} bytes): {}", broker, fileBytes.length, errorMessage, e);
      ImportHistory failed = auditWriter.finalizeFailed(batch.getId(), errorMessage, fileBytes);
      throw new ImportFailedException(
          "Failed to import file for broker "
              + broker
              + " (batchId="
              + failed.getId()
              + "): "
              + errorMessage,
          e);
    }

    ImportHistory finalized = auditWriter.finalizeApplied(batch.getId(), result);
    throwIfFailed(finalized);

    String refreshFailure = refreshDerivedData(finalized);
    if (refreshFailure != null) {
      ImportHistory notReady = auditWriter.finalizeNotReady(batch.getId(), result, refreshFailure);
      throw new ImportFailedException(
          "Import broker data applied but pipeline is not ready (batchId="
              + notReady.getId()
              + "): "
              + refreshFailure,
          null);
    }

    return toBatchResponse(
        finalized, finalized.getErrorMessage(), false);
  }

  private boolean shouldReprocessDuplicate(BrokerType broker) {
    return broker == BrokerType.IBKR || broker == BrokerType.XTB;
  }

  private static String exceptionMessage(Exception e) {
    if (e.getMessage() != null && !e.getMessage().isBlank()) {
      return e.getMessage();
    }
    return e.getClass().getSimpleName();
  }

  private String refreshDerivedData(ImportHistory batch) {
    StringBuilder failures = new StringBuilder();
    boolean projectionSucceeded = false;
    try {
      assetPriceFallbackService.populateMissingPricesFromOpenPositions();
    } catch (Exception e) {
      log.warn(
          "Asset price fallback population failed after import (batchId={}): {}",
          batch.getId(),
          e.getMessage());
      failures.append("asset price fallback failed: ").append(exceptionMessage(e));
    }

    try {
      portfolioProjectionService.recalculateAll();
      projectionSucceeded = true;
    } catch (Exception e) {
      log.warn(
          "Projection recalculation failed after import (batchId={}): {}",
          batch.getId(),
          e.getMessage());
      if (!failures.isEmpty()) {
        failures.append("; ");
      }
      failures.append("projection recalculation failed: ").append(exceptionMessage(e));
    }

    if (projectionSucceeded) {
      reconciliationRefreshService.refreshAfterImport(batch.getId());
    }

    return failures.isEmpty() ? null : failures.toString();
  }

  private static void throwIfFailed(ImportHistory batch) {
    if (batch.getStatus() == com.example.demo.infrastructure.ImportBatchStatus.FAILED) {
      throw new ImportFailedException(
          "Import broker data was rejected (batchId=" + batch.getId() + "): " + batch.getErrorMessage(),
          null);
    }
  }

  private String combineMessage(String primary, String secondary) {
    if (primary == null || primary.isBlank()) {
      return secondary;
    }
    if (secondary == null || secondary.isBlank()) {
      return primary;
    }
    return primary + " | " + secondary;
  }

  private String sha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(data));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot compute file checksum", e);
    }
  }

  private ImportBatchResponse toBatchResponse(
      ImportHistory batch, String message, boolean duplicate) {
    return new ImportBatchResponse(
        batch.getId(),
        batch.getBroker(),
        batch.getStatus(),
        nz(batch.getRowsTotal()),
        nz(batch.getRowsApplied()),
        nz(batch.getRowsFailed()),
        message,
        duplicate);
  }

  private static int nz(Integer value) {
    return value == null ? 0 : value;
  }
}
