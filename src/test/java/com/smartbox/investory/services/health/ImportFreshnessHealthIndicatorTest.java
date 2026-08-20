package com.smartbox.investory.integration.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.smartbox.investory.infrastructure.BrokerType;
import com.smartbox.investory.infrastructure.ImportBatchStatus;
import com.smartbox.investory.integration.notifications.NotificationProperties;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistory;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
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

  @Mock private ImportRepository importRepository;

  private ImportFreshnessHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    NotificationProperties properties = new NotificationProperties();
    properties.setStaleImportDays(7);
    indicator =
        new ImportFreshnessHealthIndicator(
            importRepository,
            properties,
            Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void reportsUpForRecentCompletedImport() {
    when(importRepository.findFirstByOrderByIdDesc())
        .thenReturn(Optional.of(batch(ImportBatchStatus.COMPLETED, "2026-08-10T12:00:00Z")));

    assertEquals("UP", indicator.health().getStatus().getCode());
  }

  @Test
  void reportsDownForStaleOrFailedImport() {
    when(importRepository.findFirstByOrderByIdDesc())
        .thenReturn(Optional.of(batch(ImportBatchStatus.FAILED, "2026-08-14T11:00:00Z")));

    assertEquals("DOWN", indicator.health().getStatus().getCode());
  }

  private static ImportHistory batch(ImportBatchStatus status, String finishedAt) {
    ImportHistory batch = new ImportHistory();
    batch.setId(12L);
    batch.setBroker(BrokerType.IBKR);
    batch.setStatus(status);
    batch.setFinishedAt(ZonedDateTime.parse(finishedAt));
    return batch;
  }
}
