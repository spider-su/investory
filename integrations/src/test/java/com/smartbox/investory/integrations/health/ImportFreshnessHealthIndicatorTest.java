package com.smartbox.investory.integrations.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.notifications.NotificationProperties;
import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportBatchStatus;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
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

  private static ImportHistoryEntity batch(ImportBatchStatus status, String finishedAt) {
    ImportHistoryEntity batch = new ImportHistoryEntity();
    batch.setId(12L);
    batch.setBroker(BrokerType.IBKR);
    batch.setStatus(status);
    batch.setFinishedAt(ZonedDateTime.parse(finishedAt));
    return batch;
  }
}
