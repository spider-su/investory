package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationDeliveryState;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationEventDispatcher {
  private final NotificationEventRepository events;
  private final NotificationMessageFormatterRegistry formatters;
  private final List<NotificationDeliveryChannel> channels;
  private final Clock clock;
  private final int maxAttempts;
  private final Duration retryDelay;
  private final Duration processingLease;

  public NotificationEventDispatcher(
      NotificationEventRepository events,
      NotificationMessageFormatterRegistry formatters,
      List<NotificationDeliveryChannel> channels,
      Clock clock,
      @Value("${app.notifications.dispatch.max-attempts:5}") int maxAttempts,
      @Value("${app.notifications.dispatch.retry-delay-minutes:15}") long retryDelayMinutes,
      @Value("${app.notifications.dispatch.processing-lease-minutes:5}") long leaseMinutes) {
    this.events = events;
    this.formatters = formatters;
    this.channels = List.copyOf(channels);
    this.clock = clock;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.retryDelay = Duration.ofMinutes(Math.max(1, retryDelayMinutes));
    this.processingLease = Duration.ofMinutes(Math.max(1, leaseMinutes));
  }

  public int dispatchPending() {
    if (channels.isEmpty()) {
      log.debug("No notification delivery adapter enabled; pending events retained");
      return 0;
    }
    Instant now = clock.instant();
    List<Long> dueIds = events.findDueIds(maxAttempts, now);
    int delivered = 0;
    for (Long id : dueIds) {
      String token = UUID.randomUUID().toString();
      if (events.claim(id, token, now, now.plus(processingLease), maxAttempts) != 1) continue;
      NotificationEventEntity event = events.findById(id).orElse(null);
      if (event == null) continue;
      try {
        String message = formatters.format(event);
        for (NotificationDeliveryChannel channel : channels) channel.send(message);
        if (events.markDelivered(id, token, now) == 1) delivered++;
      } catch (Exception exception) {
        int attempts = event.getAttemptCount();
        String error = safeError(exception);
        NotificationDeliveryState state =
            attempts >= maxAttempts
                ? NotificationDeliveryState.EXHAUSTED
                : NotificationDeliveryState.RETRYABLE;
        events.markFailed(
            id, token, state.name(), now.plus(retryDelay.multipliedBy(attempts)), error);
        log.warn("Notification delivery failed eventId={} attempt={}: {}", id, attempts, error);
        if (state == NotificationDeliveryState.EXHAUSTED) {
          log.error("Notification event exhausted eventId={} attempts={}", id, attempts);
        }
      }
    }
    return delivered;
  }

  private static String safeError(Exception exception) {
    String value = exception.getMessage();
    if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
    value = value.replaceAll("[\\r\\n]+", " ");
    return value.substring(0, Math.min(1000, value.length()));
  }
}
