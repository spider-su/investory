package com.smartbox.investory.integrations.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.notifications.application.NotificationProperties;
import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader.ImportOperationsSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Import Freshness Health Indicator")
class ImportFreshnessHealthIndicatorTest {

  @Mock private ImportOperationsReader investment;

  private ImportFreshnessHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    NotificationProperties properties = new NotificationProperties();
    properties.setStaleImportDays(7);
    indicator =
        new ImportFreshnessHealthIndicator(
            investment,
            properties,
            Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneOffset.UTC));
  }

  @DisplayName("reports Up For Recent Completed Import")
  @Test
  void reportsUpForRecentCompletedImport() {
    when(investment.latestImport())
        .thenReturn(Optional.of(batch("COMPLETED", "2026-08-10T12:00:00Z")));

    assertEquals("UP", indicator.health().getStatus().getCode());
  }

  @DisplayName("reports Down For Stale Or Failed Import")
  @Test
  void reportsDownForStaleOrFailedImport() {
    when(investment.latestImport())
        .thenReturn(Optional.of(batch("FAILED", "2026-08-14T11:00:00Z")));

    assertEquals("DOWN", indicator.health().getStatus().getCode());
  }

  private static ImportOperationsSnapshot batch(String status, String finishedAt) {
    return new ImportOperationsSnapshot(
        12L, ImportBroker.IBKR, status, null, ZonedDateTime.parse(finishedAt));
  }
}
