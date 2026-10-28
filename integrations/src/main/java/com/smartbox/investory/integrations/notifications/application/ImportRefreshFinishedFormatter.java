package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ImportRefreshFinishedFormatter implements NotificationMessageFormatter {
  private final ObjectMapper objectMapper;
  private final NotificationLinkBuilder links;

  public NotificationEventType type() {
    return NotificationEventType.IMPORT_REFRESH_FINISHED;
  }

  public String format(NotificationEventEntity event) {
    var p = NotificationPayload.read(objectMapper, event);
    boolean success = "COMPLETED".equals(p.get("status"));
    return TelegramText.heading(success ? "✅" : "❌", event.getTitle())
        + "\n\n"
        + TelegramText.metric("Import", p.get("importId"))
        + "\n"
        + TelegramText.metric("Broker", p.get("broker"))
        + "\n"
        + (success
            ? "Portfolio projections are up to date."
            : "Broker operations were saved. Derived data needs attention; do not re-upload the file to retry refresh.")
        + "\n\n"
        + TelegramText.link(
            "Open portfolio", links.link("/portfolios/" + event.getPortfolioId() + "/dashboard"));
  }
}
