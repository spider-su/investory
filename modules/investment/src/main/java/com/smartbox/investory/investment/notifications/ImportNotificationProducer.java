package com.smartbox.investory.investment.notifications;

import com.smartbox.investory.investment.imports.ImportBatchStatus;
import com.smartbox.investory.investment.imports.ImportSourceType;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.shared.notifications.ImportFinalizedEvent;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportNotificationProducer {
  private final NotificationEventPublisher events;
  private final ApplicationEventPublisher applicationEvents;

  public boolean publishFinalized(ImportHistoryEntity batch) {
    if (batch.getStatus() != ImportBatchStatus.FAILED
        && batch.getStatus() != ImportBatchStatus.PARTIAL) return false;

    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("importId", batch.getId().toString());
    payload.put("broker", batch.getBroker().name());
    payload.put("source", batch.getSourceType() == null ? "UNKNOWN" : batch.getSourceType().name());
    safeReference(batch).ifPresent(value -> payload.put("reference", value));
    payload.put("status", batch.getStatus().name());
    int total = nz(batch.getRowsTotal());
    int applied = nz(batch.getRowsApplied());
    int failed = nz(batch.getRowsFailed());
    payload.put("processedCount", Integer.toString(total));
    payload.put("importedCount", Integer.toString(applied));
    payload.put("skippedCount", Integer.toString(Math.max(0, total - applied - failed)));
    payload.put("errorCount", Integer.toString(failed));
    conciseFailure(batch.getErrorMessage()).ifPresent(value -> payload.put("failure", value));

    NotificationCandidate candidate =
        new NotificationCandidate(
            NotificationEventType.IMPORT_FAILED_OR_PARTIAL,
            batch.getStatus() == ImportBatchStatus.FAILED
                ? NotificationSeverity.ERROR
                : NotificationSeverity.WARNING,
            null,
            "IMPORT_HISTORY",
            batch.getId().toString(),
            "IMPORT_FAILED_OR_PARTIAL:" + batch.getId() + ":" + batch.getStatus(),
            batch.getStatus() == ImportBatchStatus.FAILED
                ? "Import failed"
                : "Import completed partially",
            payload,
            batch.getFinishedAt() == null ? Instant.now() : batch.getFinishedAt().toInstant());
    boolean published = events.publish(candidate);
    if (published) applicationEvents.publishEvent(new ImportFinalizedEvent(candidate));
    return published;
  }

  private static java.util.Optional<String> safeReference(ImportHistoryEntity batch) {
    String value = batch.getFileName();
    if (value == null || value.isBlank()) {
      value = batch.getSourceType() == ImportSourceType.TELEGRAM ? null : batch.getSourceRef();
    }
    if (value == null || value.isBlank()) return java.util.Optional.empty();
    value = value.replace('\\', '/');
    value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\t]+", " ").trim();
    return java.util.Optional.of(value.substring(0, Math.min(160, value.length())));
  }

  private static java.util.Optional<String> conciseFailure(String value) {
    if (value == null || value.isBlank()) return java.util.Optional.empty();
    String firstLine = value.split("[\\r\\n]", 2)[0].replaceAll("\\s+", " ").trim();
    return java.util.Optional.of(firstLine.substring(0, Math.min(300, firstLine.length())));
  }

  private static int nz(Integer value) {
    return value == null ? 0 : value;
  }
}
