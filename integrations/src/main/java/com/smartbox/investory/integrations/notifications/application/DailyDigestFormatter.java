package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class DailyDigestFormatter implements NotificationMessageFormatter {
  private final ObjectMapper objectMapper;

  @Override
  public NotificationEventType type() {
    return NotificationEventType.DAILY_DIGEST;
  }

  @Override
  public String format(NotificationEventEntity event) {
    String message =
        NotificationPayload.read(objectMapper, event).getOrDefault("message", event.getTitle());
    return TelegramText.heading("📊", "Daily digest") + "\n\n" + TelegramText.escape(message);
  }
}
