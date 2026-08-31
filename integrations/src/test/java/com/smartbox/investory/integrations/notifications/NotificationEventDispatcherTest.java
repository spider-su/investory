package com.smartbox.investory.integrations.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.notifications.infrastructure.NotificationDeliveryState;
import com.smartbox.investory.integrations.notifications.infrastructure.NotificationEventEntity;
import com.smartbox.investory.integrations.notifications.infrastructure.NotificationEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationEventDispatcherTest {
  private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");
  private final NotificationEventRepository events =
      Mockito.mock(NotificationEventRepository.class);
  private final NotificationMessageFormatterRegistry formatters =
      Mockito.mock(NotificationMessageFormatterRegistry.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void confirmedDeliveryMarksEventDelivered() {
    NotificationDeliveryChannel channel = Mockito.mock(NotificationDeliveryChannel.class);
    NotificationEventEntity event = pending();
    when(events
            .findTop50ByDeliveryStateInAndAttemptCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                any(), anyInt(), any()))
        .thenReturn(List.of(event));
    when(formatters.format(event)).thenReturn("message");
    NotificationEventDispatcher dispatcher =
        new NotificationEventDispatcher(events, formatters, List.of(channel), clock, 3, 5);

    assertEquals(1, dispatcher.dispatchPending());

    verify(channel).send("message");
    assertEquals(NotificationDeliveryState.DELIVERED, event.getDeliveryState());
    assertEquals(NOW, event.getDeliveredAt());
    assertEquals(1, event.getAttemptCount());
    assertNull(event.getLastError());
  }

  @Test
  void failedDeliveryRemainsRetryable() {
    NotificationDeliveryChannel channel = Mockito.mock(NotificationDeliveryChannel.class);
    NotificationEventEntity event = pending();
    when(events
            .findTop50ByDeliveryStateInAndAttemptCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                any(), anyInt(), any()))
        .thenReturn(List.of(event));
    when(formatters.format(event)).thenReturn("message");
    Mockito.doThrow(new IllegalStateException("temporary failure")).when(channel).send("message");
    NotificationEventDispatcher dispatcher =
        new NotificationEventDispatcher(events, formatters, List.of(channel), clock, 3, 5);

    assertEquals(0, dispatcher.dispatchPending());

    assertEquals(NotificationDeliveryState.RETRYABLE, event.getDeliveryState());
    assertEquals(1, event.getAttemptCount());
    assertEquals(NOW.plusSeconds(300), event.getNextAttemptAt());
  }

  @Test
  void absentAdapterLeavesPendingEventsUntouched() {
    NotificationEventDispatcher dispatcher =
        new NotificationEventDispatcher(events, formatters, List.of(), clock, 3, 5);

    assertEquals(0, dispatcher.dispatchPending());
    verify(events, never())
        .findTop50ByDeliveryStateInAndAttemptCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            any(), anyInt(), any());
  }

  private static NotificationEventEntity pending() {
    NotificationEventEntity event = new NotificationEventEntity();
    event.setId(1L);
    event.setDeliveryState(NotificationDeliveryState.PENDING);
    event.setCreatedAt(NOW);
    event.setNextAttemptAt(NOW);
    return event;
  }
}
