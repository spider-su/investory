package com.smartbox.investory.shared.notifications;

/** Persists a candidate idempotently. Returns true only when a new event was created. */
@FunctionalInterface
public interface NotificationEventPublisher {
  boolean publish(NotificationCandidate candidate);

  static NotificationEventPublisher noop() {
    return candidate -> false;
  }
}
