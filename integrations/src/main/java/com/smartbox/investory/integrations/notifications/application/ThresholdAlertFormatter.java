package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ThresholdAlertFormatter implements NotificationMessageFormatter {
  private final ObjectMapper objectMapper;

  @Override
  public NotificationEventType type() {
    return NotificationEventType.THRESHOLD_ALERT;
  }

  @Override
  public String format(NotificationEventEntity event) {
    return NotificationPayload.read(objectMapper, event).getOrDefault("message", event.getTitle());
  }
}
