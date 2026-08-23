package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.accounting.InvestmentCalculationCache;
import com.smartbox.investory.investment.accounting.PortfolioProjectionService;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.investment.market.price.AssetPriceFallbackService;
import com.smartbox.investory.investment.market.price.PriceHistoryCoverageService;
import com.smartbox.investory.investment.reconciliation.ReconciliationRefreshService;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final ImportSourceEvidenceService sourceEvidenceService;
  private final AssetPriceFallbackService assetPriceFallbackService;
  private final PortfolioProjectionService portfolioProjectionService;
  private final ReconciliationRefreshService reconciliationRefreshService;

  @Autowired(required = false)
  private PriceHistoryCoverageService priceHistoryCoverageService;

  @Autowired(required = false)
  private InvestmentCalculationCache calculationCache;

  @Autowired
  public ImportOrchestratorService(
      List<BrokerImportParser> parsers,
      ImportBatchAuditWriter auditWriter,
      ImportSourceEvidenceService sourceEvidenceService,
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
    this.sourceEvidenceService = sourceEvidenceService;
    this.assetPriceFallbackService = assetPriceFallbackService;
    this.portfolioProjectionService = portfolioProjectionService;
    this.reconciliationRefreshService = reconciliationRefreshService;
  }

  /** Source-compatible constructor for focused parser/orchestrator unit tests. */
  public ImportOrchestratorService(
      List<BrokerImportParser> parsers,
      ImportBatchAuditWriter auditWriter,
      AssetPriceFallbackService assetPriceFallbackService,
      PortfolioProjectionService portfolioProjectionService,
      ReconciliationRefreshService reconciliationRefreshService) {
    this(
        parsers,
        auditWriter,
        null,
        assetPriceFallbackService,
        portfolioProjectionService,
        reconciliationRefreshService);
  }

  public ImportBatchResponse importFile(
      BrokerType broker,
      byte[] fileBytes,
      String fileName,
      ImportSourceType sourceType,
      String sourceRef) {
    long totalStarted = System.nanoTime();
    try {
      return importFileMeasured(broker, fileBytes, fileName, sourceType, sourceRef);
    } finally {
      log.info("IMPORT PERF total={}ms", elapsedMillis(totalStarted));
    }
  }

  private ImportBatchResponse importFileMeasured(
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
    Optional<ImportHistoryEntity> existing = auditWriter.findExistingAppliedBatch(broker, checksum);
    if (existing.isPresent()) {
      if (shouldReprocessDuplicate(broker)) {
        ImportHistoryEntity original = existing.get();
        ImportHistoryEntity batch = auditWriter.startReprocessBatch(original);
        var sourceFile =
            sourceEvidenceService == null
                ? null
                : sourceEvidenceService.storeArtifact(batch, fileBytes, contentType(fileName));
        ImportHistoryEntity reloaded;
        try {
          ImportExecutionResult result = runParser(parser, batch, sourceFile, fileBytes, fileName);
          reloaded = finalizeAppliedTimed(batch.getId(), result);
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
          log.warn(
              "Duplicate import repair failed for batchId={}: {}", batch.getId(), errorMessage, e);
          ImportHistoryEntity failed =
              auditWriter.finalizeFailed(batch.getId(), errorMessage, fileBytes);
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
      ImportHistoryEntity batch = existing.get();
      return toBatchResponse(batch, "File already imported, returning existing batch", true);
    }

    ImportHistoryEntity batch =
        auditWriter.startBatch(broker, sourceType, sourceRef, fileName, checksum);
    var sourceFile =
        sourceEvidenceService == null
            ? null
            : sourceEvidenceService.storeArtifact(batch, fileBytes, contentType(fileName));

    ImportExecutionResult result;
    try {
      result = runParser(parser, batch, sourceFile, fileBytes, fileName);
    } catch (Exception e) {
      String errorMessage = exceptionMessage(e);
      log.warn(
          "Broker import failed for {} ({} bytes): {}", broker, fileBytes.length, errorMessage, e);
      ImportHistoryEntity failed =
          auditWriter.finalizeFailed(batch.getId(), errorMessage, fileBytes);
      throw new ImportFailedException(
          "Failed to import file for broker "
              + broker
              + " (batchId="
              + failed.getId()
              + "): "
              + errorMessage,
          e);
    }

    ImportHistoryEntity finalized = finalizeAppliedTimed(batch.getId(), result);
    throwIfFailed(finalized);

    String refreshFailure = refreshDerivedData(finalized);
    if (refreshFailure != null) {
      ImportHistoryEntity notReady =
          auditWriter.finalizeNotReady(batch.getId(), result, refreshFailure);
      throw new ImportFailedException(
          "Import broker data applied but pipeline is not ready (batchId="
              + notReady.getId()
              + "): "
              + refreshFailure,
          null);
    }

    if (priceHistoryCoverageService != null) {
      try {
        priceHistoryCoverageService.ensurePortfolioCoverage(null);
      } catch (Exception e) {
        log.warn(
            "Post-import price-history coverage failed (batchId={}): {}",
            finalized.getId(),
            e.getMessage());
      }
    }

    return toBatchResponse(finalized, finalized.getErrorMessage(), false);
  }

  private boolean shouldReprocessDuplicate(BrokerType broker) {
    return broker == BrokerType.IBKR || broker == BrokerType.XTB;
  }

  private ImportExecutionResult runParser(
      BrokerImportParser parser,
      ImportHistoryEntity batch,
      com.smartbox.investory.investment.infrastructure.persistence.imports.ImportSourceFileEntity
          sourceFile,
      byte[] fileBytes,
      String fileName)
      throws Exception {
    long parserStarted = System.nanoTime();
    try {
      if (sourceEvidenceService == null) {
        return parser.importFile(new ByteArrayInputStream(fileBytes), fileName);
      }
      try (ImportSourceEvidenceService.Scope ignored =
          sourceEvidenceService.open(batch, sourceFile, null)) {
        return parser.importFile(new ByteArrayInputStream(fileBytes), fileName);
      }
    } finally {
      // Includes the proxied transaction completion when the parser is a Spring bean.
      log.info("IMPORT PERF parser-transaction={}ms", elapsedMillis(parserStarted));
    }
  }

  private static String contentType(String fileName) {
    if (fileName == null) return "application/octet-stream";
    String lower = fileName.toLowerCase(java.util.Locale.ROOT);
    if (lower.endsWith(".csv")) return "text/csv";
    if (lower.endsWith(".zip")) return "application/zip";
    if (lower.endsWith(".xlsx"))
      return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    return "application/octet-stream";
  }

  private static String exceptionMessage(Exception e) {
    if (e.getMessage() != null && !e.getMessage().isBlank()) {
      return e.getMessage();
    }
    return e.getClass().getSimpleName();
  }

  private String refreshDerivedData(ImportHistoryEntity batch) {
    StringBuilder failures = new StringBuilder();
    boolean projectionSucceeded = false;
    long fallbackStarted = System.nanoTime();
    try {
      assetPriceFallbackService.populateMissingPricesFromOpenPositions();
    } catch (Exception e) {
      log.warn(
          "AssetEntity price fallback population failed after import (batchId={}): {}",
          batch.getId(),
          e.getMessage());
      failures.append("asset price fallback failed: ").append(exceptionMessage(e));
    } finally {
      log.info("IMPORT PERF asset-price-fallback={}ms", elapsedMillis(fallbackStarted));
    }

    long projectionStarted = System.nanoTime();
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
    } finally {
      log.info("IMPORT PERF projection={}ms", elapsedMillis(projectionStarted));
    }

    if (projectionSucceeded) {
      if (calculationCache != null) {
        calculationCache.invalidate();
      }
      long reconciliationStarted = System.nanoTime();
      reconciliationRefreshService.refreshAfterImport(batch.getId());
      log.info("IMPORT PERF reconciliation-schedule={}ms", elapsedMillis(reconciliationStarted));
    }

    return failures.isEmpty() ? null : failures.toString();
  }

  private static void throwIfFailed(ImportHistoryEntity batch) {
    if (batch.getStatus() == com.smartbox.investory.investment.imports.ImportBatchStatus.FAILED) {
      throw new ImportFailedException(
          "Import broker data was rejected (batchId="
              + batch.getId()
              + "): "
              + batch.getErrorMessage(),
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
      ImportHistoryEntity batch, String message, boolean duplicate) {
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

  private ImportHistoryEntity finalizeAppliedTimed(Long batchId, ImportExecutionResult result) {
    long started = System.nanoTime();
    try {
      return auditWriter.finalizeApplied(batchId, result);
    } finally {
      log.info("IMPORT PERF finalize-audit={}ms", elapsedMillis(started));
    }
  }

  private static long elapsedMillis(long started) {
    return (System.nanoTime() - started) / 1_000_000L;
  }
}
