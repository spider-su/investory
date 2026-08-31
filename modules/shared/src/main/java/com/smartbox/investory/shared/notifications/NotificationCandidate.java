package com.smartbox.investory.shared.notifications;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Channel-neutral fact offered by a domain/application producer for durable delivery. */
public record NotificationCandidate(
    NotificationEventType type,
    NotificationSeverity severity,
    Long portfolioId,
    String sourceEntityType,
    String sourceEntityId,
    String fingerprint,
    String title,
    Map<String, String> payload,
    Instant createdAt) {

  public NotificationCandidate {
    Objects.requireNonNull(type, "Notification type is required");
    Objects.requireNonNull(severity, "Notification severity is required");
    Objects.requireNonNull(sourceEntityType, "Source entity type is required");
    Objects.requireNonNull(sourceEntityId, "Source entity id is required");
    Objects.requireNonNull(fingerprint, "Fingerprint is required");
    Objects.requireNonNull(title, "Title is required");
    Objects.requireNonNull(createdAt, "Creation time is required");
    payload = com.smartbox.investory.shared.util.CollectionUtils.immutableMapOrEmpty(payload);
  }
}
