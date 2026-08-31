package com.smartbox.investory.integrations.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.integrations.notifications.infrastructure.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        new StringBuilder("🚨 ")
            .append(event.getTitle())
            .append("\nImport: ")
            .append(p.get("importId"))
            .append(" · ")
            .append(p.get("broker"))
            .append(" · ")
            .append(p.get("status"))
            .append("\nSource: ")
            .append(p.getOrDefault("source", "Unavailable"));
    if (p.containsKey("reference")) message.append(" · ").append(p.get("reference"));
    message
        .append("\nRows total/imported/skipped/errors: ")
        .append(p.get("processedCount"))
        .append('/')
        .append(p.get("importedCount"))
        .append('/')
        .append(p.get("skippedCount"))
        .append('/')
        .append(p.get("errorCount"));
    if (p.containsKey("failure")) message.append("\nCause: ").append(p.get("failure"));
    return message
        .append("\n")
        .append(links.link("/dashboard/reconciliation?importId=" + p.get("importId")))
        .toString();
  }
}
