package com.smartbox.investory.integrations.health;

import com.smartbox.investory.integrations.notifications.application.NotificationProperties;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader.ImportOperationsSnapshot;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("importFreshness")
public class ImportFreshnessHealthIndicator implements HealthIndicator {

  private final ImportOperationsReader investment;
  private final NotificationProperties properties;
  private final Clock clock;

  public ImportFreshnessHealthIndicator(
      ImportOperationsReader investment, NotificationProperties properties, Clock clock) {
    this.investment = investment;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public Health health() {
    return investment
        .latestImport()
        .map(this::healthFor)
        .orElseGet(
            () -> Health.down().withDetail("reason", "No broker imports recorded yet.").build());
  }

  private Health healthFor(ImportOperationsSnapshot batch) {
    ZonedDateTime importedAt = batch.finishedAt() != null ? batch.finishedAt() : batch.startedAt();
    if (importedAt == null) {
      return Health.down().withDetail("reason", "Latest import has no timestamp.").build();
    }
    long ageDays =
        ChronoUnit.DAYS.between(importedAt.toLocalDate(), ZonedDateTime.now(clock).toLocalDate());
    Health.Builder builder =
        "COMPLETED".equals(batch.status()) && ageDays < properties.getStaleImportDays()
            ? Health.up()
            : Health.down();
    return builder
        .withDetail("batchId", batch.batchId())
        .withDetail("broker", batch.broker())
        .withDetail("status", batch.status())
        .withDetail("lastImportAt", importedAt)
        .withDetail("ageDays", ageDays)
        .withDetail("staleAfterDays", properties.getStaleImportDays())
        .build();
  }
}
