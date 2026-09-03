package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationDeliveryState;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationAdminService {
  private final NotificationEventRepository events;
  private final Clock clock;

  public List<NotificationEventView> list() {
    return events.findTop100ByOrderByCreatedAtDesc().stream()
        .map(NotificationEventView::of)
        .toList();
  }

  public boolean replay(long id) {
    return events.replayExhausted(id, clock.instant()) == 1;
  }

  public NotificationQueueSummary summary() {
    return new NotificationQueueSummary(
        events.countByDeliveryState(NotificationDeliveryState.PENDING),
        events.countByDeliveryState(NotificationDeliveryState.RETRYABLE),
        events.countByDeliveryState(NotificationDeliveryState.PROCESSING),
        events.countByDeliveryState(NotificationDeliveryState.DELIVERED),
        events.countByDeliveryState(NotificationDeliveryState.EXHAUSTED),
        events.findFirstCreatedAtByDeliveryStateOrderByCreatedAtAsc(
            NotificationDeliveryState.PENDING));
  }

  public record NotificationQueueSummary(
      long pending,
      long retryable,
      long processing,
      long delivered,
      long exhausted,
      java.util.Optional<java.time.Instant> oldestPendingCreatedAt) {}

  public record NotificationEventView(
      long id,
      String type,
      java.time.Instant createdAt,
      int attemptCount,
      java.time.Instant nextAttemptAt,
      java.time.Instant lastAttemptAt,
      String lastError,
      String deliveryState) {
    static NotificationEventView of(NotificationEventEntity event) {
      return new NotificationEventView(
          event.getId(),
          event.getEventType().name(),
          event.getCreatedAt(),
          event.getAttemptCount(),
          event.getNextAttemptAt(),
          event.getLastAttemptAt(),
          event.getLastError(),
          event.getDeliveryState().name());
    }
  }
}
