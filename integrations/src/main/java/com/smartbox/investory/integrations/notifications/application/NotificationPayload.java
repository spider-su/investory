package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class NotificationPayload {
  private NotificationPayload() {}

  static Map<String, String> read(ObjectMapper objectMapper, NotificationEventEntity event) {
    return event.getPayload() == null ? Map.of() : event.getPayload();
  }
}
