package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.investment.api.operations.ImportOperationsReader;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader.ImportOperationsSnapshot;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fires when no successful import batch has been recorded for longer than the configured threshold.
 */
@Component
@RequiredArgsConstructor
public class StaleImportAlertRule implements AlertRule {

  private final ImportOperationsReader investment;
  private final NotificationProperties properties;
  private final ApplicationTime applicationTime;

  @Override
  public String code() {
    return "STALE_IMPORT";
  }

  @Override
  public Optional<String> evaluate() {
    Optional<ImportOperationsSnapshot> latest = investment.latestImport();
    if (latest.isEmpty()) {
      return Optional.of("No broker imports recorded yet.");
    }
    ImportOperationsSnapshot batch = latest.get();
    ZonedDateTime ts = batch.finishedAt() != null ? batch.finishedAt() : batch.startedAt();
    if (ts == null) {
      return Optional.empty();
    }
    long ageDays =
        ChronoUnit.DAYS.between(
            ts.withZoneSameInstant(applicationTime.businessZone()).toLocalDate(),
            applicationTime.today());
    int threshold = properties.getStaleImportDays();
    if (ageDays >= threshold || "FAILED".equals(batch.status())) {
      return Optional.of(
          String.format(
              "Stale import: last batch #%d (%s, %s) is %d day(s) old.",
              batch.batchId(), batch.broker(), batch.status(), ageDays));
    }
    return Optional.empty();
  }
}
