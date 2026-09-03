package com.smartbox.investory.integrations.notifications.application;

import java.util.List;
import java.util.Optional;

/**
 * A single, self-contained portfolio rule. Returns a message when the rule fires. Add a new rule by
 * creating another @Component implementing this interface; the {@link NotificationService} will
 * pick it up automatically.
 */
public interface AlertRule {

  /** Stable identifier (used for logging / future deduplication). */
  String code();

  /** Empty when the rule does not fire. */
  Optional<String> evaluate();

  /** Returns the individual incidents observed by this rule. */
  default List<AlertObservation> evaluateObservations() {
    return evaluate()
        .map(message -> List.of(new AlertObservation("", message)))
        .orElseGet(List::of);
  }
}
