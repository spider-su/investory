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
public class IntegrationJobAlertFormatter implements NotificationMessageFormatter {
  private final ObjectMapper objectMapper;
  private final NotificationLinkBuilder links;

  @Override
  public NotificationEventType type() {
    return NotificationEventType.INTEGRATION_JOB_ALERT;
  }

  @Override
  public String format(NotificationEventEntity event) {
    Map<String, String> payload = NotificationPayload.read(objectMapper, event);
    StringBuilder message =
        new StringBuilder(TelegramText.heading("🚨", event.getTitle()))
            .append("\n\n")
            .append(TelegramText.metric("Integration", payload.get("integration")))
            .append("\n")
            .append(TelegramText.metric("Job", payload.get("jobType")))
            .append("\n")
            .append(TelegramText.metric("Status", payload.get("status")));
    if (payload.containsKey("expectedAt"))
      message.append("\n").append(TelegramText.metric("Expected by", payload.get("expectedAt")));
    if (payload.containsKey("error"))
      message.append("\n").append(TelegramText.metric("Cause", payload.get("error")));
    String type = payload.get("integrationType");
    String pluginId = payload.get("integration");
    String jobType = payload.get("jobType");
    String retryPath =
        "/api/v1/admin/integrations/" + type + "/" + pluginId + "/jobs/" + jobType + "/run";
    return message
        .append("\n\n")
        .append(
            TelegramText.link("Open integration settings", links.link("/settings/integrations")))
        .append("\nRerun with POST ")
        .append(TelegramText.escape(retryPath))
        .toString();
  }
}
