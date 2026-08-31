package com.smartbox.investory.integrations.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.notifications.NotificationProperties;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader.ImportOperationsSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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

  @Test
  void reportsUpForRecentCompletedImport() {
    when(investment.latestImport())
        .thenReturn(Optional.of(batch("COMPLETED", "2026-08-10T12:00:00Z")));

    assertEquals("UP", indicator.health().getStatus().getCode());
  }

  @Test
  void reportsDownForStaleOrFailedImport() {
    when(investment.latestImport())
        .thenReturn(Optional.of(batch("FAILED", "2026-08-14T11:00:00Z")));

    assertEquals("DOWN", indicator.health().getStatus().getCode());
  }

  private static ImportOperationsSnapshot batch(String status, String finishedAt) {
    return new ImportOperationsSnapshot(12L, "IBKR", status, null, ZonedDateTime.parse(finishedAt));
  }
}
