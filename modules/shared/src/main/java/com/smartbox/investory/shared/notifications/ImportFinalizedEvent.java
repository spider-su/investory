package com.smartbox.investory.shared.notifications;

import java.util.Objects;

/** Internal signal emitted after a finalized import has been written to the outbox. */
public record ImportFinalizedEvent(NotificationCandidate candidate) {
  public ImportFinalizedEvent {
    Objects.requireNonNull(candidate, "Notification candidate is required");
  }
}
