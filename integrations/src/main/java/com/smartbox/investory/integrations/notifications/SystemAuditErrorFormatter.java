package com.smartbox.investory.integrations.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.integrations.notifications.infrastructure.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        + links.link("/dashboard/reconciliation?auditRunId=" + p.get("auditId"));
  }
}
