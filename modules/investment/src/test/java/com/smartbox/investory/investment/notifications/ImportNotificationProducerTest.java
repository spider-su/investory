package com.smartbox.investory.investment.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportBatchStatus;
import com.smartbox.investory.investment.imports.ImportSourceType;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistoryEntity;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("Import Notification Producer")
class ImportNotificationProducerTest {
  private final NotificationEventPublisher events = Mockito.mock(NotificationEventPublisher.class);
  private final ApplicationEventPublisher applicationEvents =
      Mockito.mock(ApplicationEventPublisher.class);
  private final ImportNotificationProducer producer =
      new ImportNotificationProducer(events, applicationEvents);

  @DisplayName("publishes Failed And Partial With Stable Final Status Fingerprint")
  @Test
  void publishesFailedAndPartialWithStableFinalStatusFingerprint() {
    when(events.publish(any())).thenReturn(true);

    assertTrue(producer.publishFinalized(batch(7L, ImportBatchStatus.FAILED)));
    assertTrue(producer.publishFinalized(batch(8L, ImportBatchStatus.PARTIAL)));

    ArgumentCaptor<NotificationCandidate> candidates =
        ArgumentCaptor.forClass(NotificationCandidate.class);
    verify(events, Mockito.times(2)).publish(candidates.capture());
    assertTrue(candidates.getAllValues().get(0).fingerprint().endsWith(":7:FAILED"));
    assertTrue(candidates.getAllValues().get(1).fingerprint().endsWith(":8:PARTIAL"));
  }

  @DisplayName("successful Import Does Not Publish")
  @Test
  void successfulImportDoesNotPublish() {
    assertFalse(producer.publishFinalized(batch(9L, ImportBatchStatus.COMPLETED)));
    verify(events, never()).publish(any());
  }

  @DisplayName("failure Payload Uses Only Concise First Line And Hides Telegram Chat Reference")
  @Test
  void failurePayloadUsesOnlyConciseFirstLineAndHidesTelegramChatReference() {
    when(events.publish(any())).thenReturn(true);
    ImportHistoryEntity batch = batch(10L, ImportBatchStatus.FAILED);
    batch.setSourceType(ImportSourceType.TELEGRAM);
    batch.setSourceRef("123456789");
    batch.setFileName(null);
    batch.setErrorMessage("invalid header\nSECRET RAW IMPORT ROW");

    producer.publishFinalized(batch);

    ArgumentCaptor<NotificationCandidate> candidate =
        ArgumentCaptor.forClass(NotificationCandidate.class);
    verify(events).publish(candidate.capture());
    assertFalse(candidate.getValue().payload().containsKey("reference"));
    assertTrue(candidate.getValue().payload().get("failure").equals("invalid header"));
    assertFalse(candidate.getValue().payload().toString().contains("SECRET"));
  }

  private static ImportHistoryEntity batch(Long id, ImportBatchStatus status) {
    ImportHistoryEntity batch = new ImportHistoryEntity();
    batch.setId(id);
    batch.setBroker(BrokerType.IBKR);
    batch.setSourceType(ImportSourceType.MANUAL);
    batch.setFileName("statement.csv");
    batch.setStatus(status);
    batch.setRowsTotal(10);
    batch.setRowsApplied(status == ImportBatchStatus.FAILED ? 0 : 8);
    batch.setRowsFailed(status == ImportBatchStatus.COMPLETED ? 0 : 2);
    batch.setFinishedAt(ZonedDateTime.now());
    return batch;
  }
}
