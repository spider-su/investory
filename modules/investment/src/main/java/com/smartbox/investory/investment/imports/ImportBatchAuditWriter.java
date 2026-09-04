package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
import com.smartbox.investory.investment.notifications.ImportNotificationProducer;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit rows for {@link ImportOrchestratorService} in their own transactions.
 *
 * <p>Splitting the writes off the orchestrator solves a correctness bug: the orchestrator used to
 * be {@code @Transactional}, so when the parser failed and the catch block tried to persist a
 * {@code FAILED} batch, Spring rolled the whole transaction back and the audit trail was lost. Each
 * method here runs in {@link Propagation#REQUIRES_NEW}, so the audit rows survive even when the
 * surrounding parser transaction rolls back.
 */
@Component
@RequiredArgsConstructor
public class ImportBatchAuditWriter {

  /** Cap raw text copied into the import batch diagnostic. */
  static final int RAW_PAYLOAD_LIMIT = 8 * 1024;

  private final ImportRepository importRepository;
  private final ApplicationTime applicationTime;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ImportNotificationProducer notificationProducer;

  @Transactional(readOnly = true)
  public Optional<ImportHistoryEntity> findExistingAppliedBatch(
      Long portfolioId, BrokerType broker, String sha256) {
    return importRepository
        .findFirstByPortfolioIdAndBrokerAndFileSha256AndStatusOrderByAttemptNoDesc(
            portfolioId, broker, sha256, ImportBatchStatus.COMPLETED);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ImportHistoryEntity startBatch(
      Long portfolioId,
      BrokerType broker,
      ImportSourceType sourceType,
      String sourceRef,
      String fileName,
      String sha256) {
    Optional<ImportHistoryEntity> existing =
        importRepository.findFirstByPortfolioIdAndBrokerAndFileSha256OrderByAttemptNoDesc(
            portfolioId, broker, sha256);
    ImportHistoryEntity batch = new ImportHistoryEntity();
    batch.setPortfolioId(portfolioId);
    batch.setBroker(broker);
    batch.setSourceType(sourceType);
    batch.setSourceRef(sourceRef);
    batch.setFileName(fileName);
    batch.setFileSha256(sha256);
    batch.setStartedAt(applicationTime.now(applicationTime.businessZone()));
    batch.setStatus(ImportBatchStatus.STARTED);
    batch.setRowsTotal(0);
    batch.setRowsApplied(0);
    batch.setRowsFailed(0);
    batch.setAttemptNo(
        existing
            .map(previous -> previous.getAttemptNo() == null ? 2 : previous.getAttemptNo() + 1)
            .orElse(1));
    existing.map(ImportHistoryEntity::getId).ifPresent(batch::setReprocessOf);
    return importRepository.save(batch);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ImportHistoryEntity startReprocessBatch(ImportHistoryEntity original) {
    Optional<ImportHistoryEntity> latestLookup =
        original.getPortfolioId() == null
            ? importRepository.findFirstByBrokerAndFileSha256OrderByAttemptNoDesc(
                original.getBroker(), original.getFileSha256())
            : importRepository.findFirstByPortfolioIdAndBrokerAndFileSha256OrderByAttemptNoDesc(
                original.getPortfolioId(), original.getBroker(), original.getFileSha256());
    ImportHistoryEntity latest = latestLookup.orElse(original);
    ImportHistoryEntity batch = new ImportHistoryEntity();
    batch.setBroker(original.getBroker());
    batch.setPortfolioId(original.getPortfolioId());
    batch.setSourceType(original.getSourceType());
    batch.setSourceRef(original.getSourceRef());
    batch.setFileName(original.getFileName());
    batch.setFileSha256(original.getFileSha256());
    batch.setStartedAt(applicationTime.now(applicationTime.businessZone()));
    batch.setStatus(ImportBatchStatus.STARTED);
    batch.setRowsTotal(0);
    batch.setRowsApplied(0);
    batch.setRowsFailed(0);
    batch.setAttemptNo((latest.getAttemptNo() == null ? 1 : latest.getAttemptNo()) + 1);
    batch.setReprocessOf(original.getId());
    return importRepository.save(batch);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ImportHistoryEntity finalizeApplied(Long batchId, ImportExecutionResult result) {
    ImportHistoryEntity batch = importRepository.requireById(batchId);
    batch.setStatus(
        result.rowsFailed() == 0 && result.rowsApplied() == result.rowsTotal()
            ? ImportBatchStatus.COMPLETED
            : result.rowsApplied() > 0 ? ImportBatchStatus.PARTIAL : ImportBatchStatus.FAILED);
    batch.setRowsTotal(result.rowsTotal());
    batch.setRowsApplied(result.rowsApplied());
    batch.setRowsFailed(result.rowsFailed());
    batch.setErrorMessage(result.details());
    batch.setFinishedAt(applicationTime.now(applicationTime.businessZone()));
    ImportHistoryEntity saved = importRepository.save(batch);
    publishFinalized(saved);
    return saved;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ImportHistoryEntity finalizeFailed(Long batchId, String message, byte[] rawPayload) {
    ImportHistoryEntity batch = importRepository.requireById(batchId);
    batch.setStatus(ImportBatchStatus.FAILED);
    if (batch.getRowsTotal() == null || batch.getRowsTotal() < 1) {
      batch.setRowsTotal(1);
    }
    batch.setRowsApplied(0);
    batch.setRowsFailed(1);
    String payloadPreview = truncate(rawPayload);
    batch.setErrorMessage(
        payloadPreview == null || !isTextLike(rawPayload)
            ? message
            : message + "\n" + payloadPreview);
    batch.setFinishedAt(applicationTime.now(applicationTime.businessZone()));
    ImportHistoryEntity saved = importRepository.save(batch);
    publishFinalized(saved);
    return saved;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ImportHistoryEntity finalizeNotReady(
      Long batchId, ImportExecutionResult result, String message) {
    ImportHistoryEntity batch = importRepository.requireById(batchId);
    batch.setStatus(ImportBatchStatus.NOT_READY);
    batch.setRowsTotal(result.rowsTotal());
    batch.setRowsApplied(result.rowsApplied());
    batch.setRowsFailed(result.rowsFailed());
    batch.setErrorMessage(message);
    batch.setFinishedAt(applicationTime.now(applicationTime.businessZone()));
    return importRepository.save(batch);
  }

  static String truncate(byte[] rawPayload) {
    if (rawPayload == null || rawPayload.length == 0) {
      return null;
    }
    if (rawPayload.length <= RAW_PAYLOAD_LIMIT) {
      return new String(rawPayload, java.nio.charset.StandardCharsets.UTF_8).replace("\u0000", "");
    }
    byte[] head = new byte[RAW_PAYLOAD_LIMIT];
    System.arraycopy(rawPayload, 0, head, 0, RAW_PAYLOAD_LIMIT);
    return "[truncated to "
        + RAW_PAYLOAD_LIMIT
        + " of "
        + rawPayload.length
        + " bytes]\n"
        + new String(head, java.nio.charset.StandardCharsets.UTF_8).replace("\u0000", "");
  }

  private static boolean isTextLike(byte[] rawPayload) {
    if (rawPayload == null || rawPayload.length == 0) {
      return false;
    }
    int limit = Math.min(rawPayload.length, 512);
    for (int i = 0; i < limit; i++) {
      int value = rawPayload[i] & 0xff;
      if (value == 0 || (value < 0x09) || (value > 0x0d && value < 0x20)) {
        return false;
      }
    }
    return true;
  }

  private void publishFinalized(ImportHistoryEntity batch) {
    if (notificationProducer != null) notificationProducer.publishFinalized(batch);
  }
}
