package com.smartbox.investory.integrations.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.integrations.notifications.infrastructure.NotificationEventEntity;
import java.util.Map;

final class NotificationPayload {
  private NotificationPayload() {}

  static Map<String, String> read(ObjectMapper objectMapper, NotificationEventEntity event) {
    return event.getPayload() == null ? Map.of() : event.getPayload();
  }
}
