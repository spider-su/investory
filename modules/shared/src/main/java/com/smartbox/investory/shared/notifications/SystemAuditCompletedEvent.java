package com.smartbox.investory.shared.notifications;

import java.util.Objects;

/** Internal signal emitted after a system-audit notification has been written to the outbox. */
public record SystemAuditCompletedEvent(NotificationCandidate candidate) {
  public SystemAuditCompletedEvent {
    Objects.requireNonNull(candidate, "Notification candidate is required");
  }
}
