package com.smartbox.investory.shared.notifications;

import java.util.Objects;

/** Internal signal emitted after a reviewed plan revision has been written to the outbox. */
public record PlanRevisionReviewedEvent(NotificationCandidate candidate) {
  public PlanRevisionReviewedEvent {
    Objects.requireNonNull(candidate, "Notification candidate is required");
  }
}
