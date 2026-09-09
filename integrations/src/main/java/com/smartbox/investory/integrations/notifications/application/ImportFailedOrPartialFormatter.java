package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ImportFailedOrPartialFormatter implements NotificationMessageFormatter {
  private final ObjectMapper objectMapper;
  private final NotificationLinkBuilder links;

  @Override
  public NotificationEventType type() {
    return NotificationEventType.IMPORT_FAILED_OR_PARTIAL;
  }

  @Override
  public String format(NotificationEventEntity event) {
    Map<String, String> p = NotificationPayload.read(objectMapper, event);
    StringBuilder message =
        new StringBuilder(TelegramText.heading("🚨", event.getTitle()))
            .append("\n\n<b>Import:</b> ")
            .append(TelegramText.escape(p.get("importId")))
            .append(" · ")
            .append(TelegramText.escape(p.get("broker")))
            .append(" · ")
            .append(TelegramText.escape(p.get("status")))
            .append("\n<b>Source:</b> ")
            .append(TelegramText.escape(p.getOrDefault("source", "Unavailable")));
    if (p.containsKey("reference"))
      message.append(" · ").append(TelegramText.escape(p.get("reference")));
    message
        .append("\n<b>Rows total/imported/skipped/errors:</b> ")
        .append(p.get("processedCount"))
        .append('/')
        .append(p.get("importedCount"))
        .append('/')
        .append(p.get("skippedCount"))
        .append('/')
        .append(p.get("errorCount"));
    if (p.containsKey("failure"))
      message.append("\n<b>Cause:</b> ").append(TelegramText.escape(p.get("failure")));
    return message
        .append("\n\n")
        .append(TelegramText.link("Open reconciliation", links.link("/dashboard/reconciliation")))
        .toString();
  }
}
