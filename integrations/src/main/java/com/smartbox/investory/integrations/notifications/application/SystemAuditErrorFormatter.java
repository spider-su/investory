package com.smartbox.investory.integrations.notifications.application;

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
    return "🚨 "
        + event.getTitle()
        + "\nAudit: "
        + p.get("auditId")
        + " · trigger: "
        + p.get("triggerSource")
        + "\nErrors/warnings: "
        + p.get("errorCount")
        + "/"
        + p.get("warningCount")
        + "\nChecks: "
        + p.getOrDefault("checkCodes", "Unavailable")
        + "\n"
        + links.link("/dashboard/reconciliation");
  }
}
