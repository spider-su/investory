package com.smartbox.investory.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.smartbox.investory.integrations.notifications.application.NotificationDeliveryChannel;
import com.smartbox.investory.integrations.notifications.application.NotificationEventDispatcher;
import com.smartbox.investory.integrations.notifications.persistence.NotificationDeliveryState;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventRepository;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import com.smartbox.investory.shared.time.ApplicationTime;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Durable canonical event through formatting and a test-only delivery boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationDeliveryHappyInvestorIT extends FastDatabaseTest {
  @Autowired private NotificationEventRepository events;
  @Autowired private NotificationEventDispatcher dispatcher;
  @Autowired private ApplicationTime time;
  @MockitoBean private NotificationDeliveryChannel delivery;

  private Long eventId;

  @AfterEach
  void cleanUp() {
    if (eventId != null) events.deleteById(eventId);
  }

  @Test
  void persistsFormatsDeliversAndMarksCanonicalEventExactlyOnce() {
    eventId = saveEvent();

    assertThat(dispatcher.dispatchPending()).isEqualTo(1);
    var event = events.findById(eventId).orElseThrow();
    assertThat(event.getDeliveryState()).isEqualTo(NotificationDeliveryState.DELIVERED);
    assertThat(event.getDeliveredAt()).isNotNull();
    verify(delivery)
        .send(
            "🚨 Happy Investor import failed\n"
                + "Import: 7001 · IBKR · FAILED\n"
                + "Source: canonical HappyInvestor\n"
                + "Rows total/imported/skipped/errors: 21/20/0/1\n"
                + "Cause: canonical provider failure\n"
                + "http://localhost:8080/dashboard/reconciliation");

    assertThat(dispatcher.dispatchPending()).isZero();
  }

  @Test
  void failedDeliveryIsRetryableAndDoesNotClaimSuccess() {
    eventId = saveEvent();
    doThrow(new IllegalStateException("delivery unavailable")).when(delivery).send(anyString());

    assertThat(dispatcher.dispatchPending()).isZero();
    var event = events.findById(eventId).orElseThrow();
    assertThat(event.getDeliveryState()).isEqualTo(NotificationDeliveryState.RETRYABLE);
    assertThat(event.getDeliveredAt()).isNull();
    assertThat(event.getLastError()).isEqualTo("delivery unavailable");
  }

  private Long saveEvent() {
    NotificationEventEntity event = new NotificationEventEntity();
    event.setEventType(NotificationEventType.IMPORT_FAILED_OR_PARTIAL);
    event.setSeverity(NotificationSeverity.ERROR);
    event.setPortfolioId(1L);
    event.setSourceEntityType("IMPORT");
    event.setSourceEntityId("7001");
    event.setFingerprint("happy-investor-notification:" + UUID.randomUUID());
    event.setTitle("Happy Investor import failed");
    event.setPayload(
        Map.of(
            "importId", "7001",
            "broker", "IBKR",
            "status", "FAILED",
            "source", "canonical HappyInvestor",
            "processedCount", "21",
            "importedCount", "20",
            "skippedCount", "0",
            "errorCount", "1",
            "failure", "canonical provider failure"));
    event.setCreatedAt(time.now());
    event.setDeliveryState(NotificationDeliveryState.PENDING);
    event.setAttemptCount(0);
    event.setNextAttemptAt(time.now());
    return events.saveAndFlush(event).getId();
  }
}
