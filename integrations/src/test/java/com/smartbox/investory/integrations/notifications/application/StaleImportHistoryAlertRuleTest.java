package com.smartbox.investory.integrations.notifications.application;

import static com.smartbox.investory.integrations.FixedTestTime.TIME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader;
import com.smartbox.investory.investment.api.operations.ImportOperationsReader.ImportOperationsSnapshot;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stale Import History Alert Rule")
class StaleImportHistoryAlertRuleTest {

  @Mock private ImportOperationsReader investment;

  private NotificationProperties properties;
  private StaleImportAlertRule rule;

  @BeforeEach
  void setUp() {
    properties = new NotificationProperties();
    properties.setStaleImportDays(7);
    rule = new StaleImportAlertRule(investment, properties, TIME);
  }

  @DisplayName("evaluate fires When No Batches Exist")
  @Test
  void evaluate_firesWhenNoBatchesExist() {
    when(investment.latestImport()).thenReturn(Optional.empty());

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    assertTrue(result.get().contains("No broker imports"));
  }

  @DisplayName("evaluate fires When Last Batch Is Older Than Threshold")
  @Test
  void evaluate_firesWhenLastBatchIsOlderThanThreshold() {
    when(investment.latestImport())
        .thenReturn(Optional.of(batch("COMPLETED", ZonedDateTime.now().minusDays(30))));

    Optional<String> result = rule.evaluate();

    assertTrue(result.isPresent());
    assertTrue(result.get().contains("Stale import"));
  }

  @DisplayName("evaluate fires When Last Batch Failed")
  @Test
  void evaluate_firesWhenLastBatchFailed() {
    when(investment.latestImport()).thenReturn(Optional.of(batch("FAILED", ZonedDateTime.now())));

    assertTrue(rule.evaluate().isPresent());
  }

  @DisplayName("evaluate is Quiet For Fresh Applied Batch")
  @Test
  void evaluate_isQuietForFreshAppliedBatch() {
    when(investment.latestImport())
        .thenReturn(Optional.of(batch("COMPLETED", ZonedDateTime.now())));

    assertFalse(rule.evaluate().isPresent());
  }

  private static ImportOperationsSnapshot batch(String status, ZonedDateTime ts) {
    return new ImportOperationsSnapshot(1L, ImportBroker.XTB, status, ts, ts);
  }
}
