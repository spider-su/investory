package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.investment.api.operations.ImportOperationsReader;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader.ImportOperationsSnapshot;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Checks the latest attempt independently for each previously imported broker. */
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
    return evaluateObservations().stream()
        .map(AlertObservation::message)
        .reduce((a, b) -> a + "\n" + b);
  }

  @Override
  public List<AlertObservation> evaluateObservations() {
    List<ImportOperationsSnapshot> batches =
        investment.latestImportsByBroker(properties.getPortfolioId());
    if (batches.isEmpty()) {
      return List.of(new AlertObservation("", "No broker imports recorded yet."));
    }
    return batches.stream()
        .flatMap(
            batch ->
                staleMessage(batch).stream()
                    .map(message -> new AlertObservation(batch.broker().name(), message)))
        .toList();
  }

  private Optional<String> staleMessage(ImportOperationsSnapshot batch) {
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
