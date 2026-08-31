package com.smartbox.investory.integrations.health;

import com.smartbox.investory.integrations.notifications.NotificationProperties;
import com.smartbox.investory.investment.imports.ImportBatchStatus;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("importFreshness")
public class ImportFreshnessHealthIndicator implements HealthIndicator {

  private final ImportRepository importRepository;
  private final NotificationProperties properties;
  private final Clock clock;

  public ImportFreshnessHealthIndicator(
      ImportRepository importRepository, NotificationProperties properties, Clock clock) {
    this.importRepository = importRepository;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public Health health() {
    return importRepository
        .findFirstByOrderByIdDesc()
        .map(this::healthFor)
        .orElseGet(
            () -> Health.down().withDetail("reason", "No broker imports recorded yet.").build());
  }

  private Health healthFor(ImportHistoryEntity batch) {
    ZonedDateTime importedAt =
        batch.getFinishedAt() != null ? batch.getFinishedAt() : batch.getStartedAt();
    if (importedAt == null) {
      return Health.down().withDetail("reason", "Latest import has no timestamp.").build();
    }
    long ageDays =
        ChronoUnit.DAYS.between(importedAt.toLocalDate(), ZonedDateTime.now(clock).toLocalDate());
    Health.Builder builder =
        batch.getStatus() == ImportBatchStatus.COMPLETED
                && ageDays < properties.getStaleImportDays()
            ? Health.up()
            : Health.down();
    return builder
        .withDetail("batchId", batch.getId())
        .withDetail("broker", batch.getBroker())
        .withDetail("status", batch.getStatus())
        .withDetail("lastImportAt", importedAt)
        .withDetail("ageDays", ageDays)
        .withDetail("staleAfterDays", properties.getStaleImportDays())
        .build();
  }
}
