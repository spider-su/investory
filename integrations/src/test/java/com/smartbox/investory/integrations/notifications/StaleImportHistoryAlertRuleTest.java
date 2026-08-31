package com.smartbox.investory.integrations.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.operations.ImportOperationsReader;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader.ImportOperationsSnapshot;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaleImportHistoryAlertRuleTest {

  @Mock private ImportOperationsReader investment;

  private NotificationProperties properties;
  private StaleImportAlertRule rule;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.setStaleImportDays(7);
    rule = new StaleImportAlertRule(investment, properties);
  }

  @Test
  void evaluate_firesWhenNoBatchesExist() {
    when(investment.latestImport()).thenReturn(Optional.empty());

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    assertTrue(result.get().contains("No broker imports"));
  }

  @Test
  void evaluate_firesWhenLastBatchIsOlderThanThreshold() {
    when(investment.latestImport())
        .thenReturn(Optional.of(batch("COMPLETED", ZonedDateTime.now().minusDays(30))));

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    assertTrue(result.get().contains("Stale import"));
  }

  @Test
  void evaluate_firesWhenLastBatchFailed() {
    when(investment.latestImport()).thenReturn(Optional.of(batch("FAILED", ZonedDateTime.now())));

    assertTrue(rule.evaluate().isPresent());
  }

  @Test
  void evaluate_isQuietForFreshAppliedBatch() {
    when(investment.latestImport())
        .thenReturn(Optional.of(batch("COMPLETED", ZonedDateTime.now())));

    assertFalse(rule.evaluate().isPresent());
  }

  private static ImportOperationsSnapshot batch(String status, ZonedDateTime ts) {
    return new ImportOperationsSnapshot(1L, "XTB", status, ts, ts);
  }
}
