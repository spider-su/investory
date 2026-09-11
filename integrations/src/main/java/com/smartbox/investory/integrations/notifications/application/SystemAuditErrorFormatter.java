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
public class SystemAuditErrorFormatter implements NotificationMessageFormatter {
  private final ObjectMapper objectMapper;
  private final NotificationLinkBuilder links;

  @Override
  public NotificationEventType type() {
    return NotificationEventType.SYSTEM_AUDIT_ERROR;
  }

  @Override
  public String format(NotificationEventEntity event) {
    Map<String, String> p = NotificationPayload.read(objectMapper, event);
    return TelegramText.heading("🚨", event.getTitle())
        + "\n\n<b>Audit:</b> "
        + p.get("auditId")
        + " · <b>trigger:</b> "
        + p.get("triggerSource")
        + "\n<b>Errors/warnings: "
        + p.get("errorCount")
        + "/"
        + p.get("warningCount")
        + "</b>\n<b>Checks: "
        + p.getOrDefault("checkCodes", "Unavailable")
        + "</b>"
        + "\n\n"
        + TelegramText.link("Open reconciliation", links.link("/dashboard/reconciliation"));
  }
}
