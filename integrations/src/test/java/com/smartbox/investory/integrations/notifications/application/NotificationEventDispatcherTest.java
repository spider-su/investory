package com.smartbox.investory.integrations.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.integrations.notifications.persistence.NotificationDeliveryState;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventRepository;
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
  void onlyTheWorkerThatWinsTheAtomicClaimCanDeliver() {
    var channel = mock(NotificationDeliveryChannel.class);
    var event = processing();
    when(events.findDueIds(3, NOW)).thenReturn(List.of(1L));
    when(events.claim(eq(1L), anyString(), eq(NOW), any(), eq(3))).thenReturn(1, 0);
    when(events.findById(1L)).thenReturn(java.util.Optional.of(event));
    when(events.markDelivered(anyLong(), anyString(), any(Instant.class))).thenReturn(1);
    when(formatters.format(event)).thenReturn("message");
    assertThat(dispatcher(channel).dispatchPending()).isEqualTo(1);
    assertThat(dispatcher(channel).dispatchPending()).isZero();
    verify(channel, times(1)).send("message");
  }

  @Test
  void failedDeliveryBeforeLimitIsScheduledForRetry() {
    var channel = mock(NotificationDeliveryChannel.class);
    var event = processing();
    event.setAttemptCount(1);
    when(events.findDueIds(3, NOW)).thenReturn(List.of(1L));
    when(events.claim(eq(1L), anyString(), eq(NOW), any(), eq(3))).thenReturn(1);
    when(events.findById(1L)).thenReturn(java.util.Optional.of(event));
    when(formatters.format(event)).thenReturn("message");
    doThrow(new IllegalStateException("temporary")).when(channel).send("message");

    assertThat(dispatcher(channel).dispatchPending()).isZero();
    verify(events)
        .markFailed(
            eq(1L), anyString(), eq("RETRYABLE"), eq(NOW.plusSeconds(300)), eq("temporary"));
  }

  @Test
  void failureBecomesRetryableAndThenExhausted() {
    var channel = mock(NotificationDeliveryChannel.class);
    var event = processing();
    event.setAttemptCount(2);
    when(events.findDueIds(2, NOW)).thenReturn(List.of(1L));
    when(events.claim(eq(1L), anyString(), eq(NOW), any(), eq(2))).thenReturn(1);
    when(events.findById(1L)).thenReturn(java.util.Optional.of(event));
    when(formatters.format(event)).thenReturn("message");
    doThrow(new IllegalStateException("down")).when(channel).send("message");
    assertThat(dispatcher(channel, 2).dispatchPending()).isZero();
    verify(events)
        .markFailed(eq(1L), anyString(), eq("EXHAUSTED"), eq(NOW.plusSeconds(600)), eq("down"));
  }

  @Test
  void successfulDeliveryMarksDelivered() {
    var channel = mock(NotificationDeliveryChannel.class);
    var event = processing();
    when(events.findDueIds(3, NOW)).thenReturn(List.of(1L));
    when(events.claim(eq(1L), anyString(), eq(NOW), any(), eq(3))).thenReturn(1);
    when(events.findById(1L)).thenReturn(java.util.Optional.of(event));
    when(events.markDelivered(anyLong(), anyString(), any(Instant.class))).thenReturn(1);
    when(formatters.format(event)).thenReturn("message");
    assertThat(dispatcher(channel).dispatchPending()).isEqualTo(1);
    verify(events).markDelivered(eq(1L), anyString(), eq(NOW));
  }

  private NotificationEventDispatcher dispatcher(NotificationDeliveryChannel channel) {
    return dispatcher(channel, 3);
  }

  private NotificationEventDispatcher dispatcher(
      NotificationDeliveryChannel channel, int attempts) {
    return new NotificationEventDispatcher(
        events, formatters, List.of(channel), clock, attempts, 5, 5);
  }

  private static NotificationEventEntity processing() {
    var event = new NotificationEventEntity();
    event.setId(1L);
    event.setDeliveryState(NotificationDeliveryState.PROCESSING);
    event.setCreatedAt(NOW);
    event.setNextAttemptAt(NOW);
    return event;
  }
}
