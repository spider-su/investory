package com.example.demo.services.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.infrastructure.ImportBatchStatus;
import com.example.demo.infrastructure.repository.imports.ImportHistory;
import com.example.demo.infrastructure.repository.imports.ImportRepository;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaleImportHistoryAlertRuleTest {

  @Mock private ImportRepository importRepository;

  private NotificationProperties properties;
  private StaleImportAlertRule rule;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.setStaleImportDays(7);
    rule = new StaleImportAlertRule(importRepository, properties);
  }

  @Test
  void evaluate_firesWhenNoBatchesExist() {
    when(importRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    assertTrue(result.get().contains("No broker imports"));
  }

  @Test
  void evaluate_firesWhenLastBatchIsOlderThanThreshold() {
    when(importRepository.findFirstByOrderByIdDesc())
        .thenReturn(
            Optional.of(batch(ImportBatchStatus.COMPLETED, ZonedDateTime.now().minusDays(30))));

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    assertTrue(result.get().contains("Stale import"));
  }

  @Test
  void evaluate_firesWhenLastBatchFailed() {
    when(importRepository.findFirstByOrderByIdDesc())
        .thenReturn(Optional.of(batch(ImportBatchStatus.FAILED, ZonedDateTime.now())));

    assertTrue(rule.evaluate().isPresent());
  }

  @Test
  void evaluate_isQuietForFreshAppliedBatch() {
    when(importRepository.findFirstByOrderByIdDesc())
        .thenReturn(Optional.of(batch(ImportBatchStatus.COMPLETED, ZonedDateTime.now())));

    assertFalse(rule.evaluate().isPresent());
  }

  private static ImportHistory batch(ImportBatchStatus status, ZonedDateTime ts) {
    ImportHistory b = new ImportHistory();
    b.setId(1L);
    b.setBroker(BrokerType.XTB);
    b.setStatus(status);
    b.setStartedAt(ts);
    b.setFinishedAt(ts);
    return b;
  }
}
