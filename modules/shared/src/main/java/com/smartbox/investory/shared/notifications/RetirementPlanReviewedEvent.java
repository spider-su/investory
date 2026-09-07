package com.smartbox.investory.shared.notifications;

import java.util.Objects;

/** Signals that a retirement-plan review published an outbox notification. */
public record RetirementPlanReviewedEvent(NotificationCandidate candidate) {
  public RetirementPlanReviewedEvent {
    Objects.requireNonNull(candidate, "candidate");
  }
}
