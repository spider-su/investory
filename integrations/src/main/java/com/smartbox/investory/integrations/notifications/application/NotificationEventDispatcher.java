package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationDeliveryState;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationEventDispatcher {
  private final NotificationEventRepository events;
  private final NotificationMessageFormatterRegistry formatters;
  private final List<NotificationDeliveryChannel> channels;
  private final Clock clock;
  private final int maxAttempts;
  private final Duration retryDelay;

  public NotificationEventDispatcher(
      NotificationEventRepository events,
      NotificationMessageFormatterRegistry formatters,
      List<NotificationDeliveryChannel> channels,
      Clock clock,
      @Value("${app.notifications.dispatch.max-attempts:5}") int maxAttempts,
      @Value("${app.notifications.dispatch.retry-delay-minutes:15}") long retryDelayMinutes) {
    this.events = events;
    this.formatters = formatters;
    this.channels = List.copyOf(channels);
    this.clock = clock;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.retryDelay = Duration.ofMinutes(Math.max(1, retryDelayMinutes));
  }

  @Transactional
  public int dispatchPending() {
    if (channels.isEmpty()) {
      log.debug("No notification delivery adapter enabled; pending events retained");
      return 0;
    }
    Instant now = clock.instant();
    List<NotificationEventEntity> pending =
        events
            .findTop50ByDeliveryStateInAndAttemptCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                List.of(NotificationDeliveryState.PENDING, NotificationDeliveryState.RETRYABLE),
                maxAttempts,
                now);
    int delivered = 0;
    for (NotificationEventEntity event : pending) {
      try {
        String message = formatters.format(event);
        for (NotificationDeliveryChannel channel : channels) channel.send(message);
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastAttemptAt(now);
        event.setDeliveryState(NotificationDeliveryState.DELIVERED);
        event.setDeliveredAt(now);
        event.setLastError(null);
        delivered++;
      } catch (Exception exception) {
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);
        event.setLastAttemptAt(now);
        event.setLastError(safeError(exception));
        event.setDeliveryState(
            attempts >= maxAttempts
                ? NotificationDeliveryState.EXHAUSTED
                : NotificationDeliveryState.RETRYABLE);
        event.setNextAttemptAt(now.plus(retryDelay.multipliedBy(attempts)));
        log.warn(
            "Notification delivery failed eventId={} attempt={}: {}",
            event.getId(),
            attempts,
            event.getLastError());
      }
    }
    events.saveAll(pending);
    return delivered;
  }

  private static String safeError(Exception exception) {
    String value = exception.getMessage();
    if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
    value = value.replaceAll("[\\r\\n]+", " ");
    return value.substring(0, Math.min(1000, value.length()));
  }
}
