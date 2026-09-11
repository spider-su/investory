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
public class PlanBecameUnsustainableFormatter implements NotificationMessageFormatter {
  private final ObjectMapper objectMapper;
  private final NotificationLinkBuilder links;

  @Override
  public NotificationEventType type() {
    return NotificationEventType.PLAN_BECAME_UNSUSTAINABLE;
  }

  @Override
  public String format(NotificationEventEntity event) {
    Map<String, String> p = NotificationPayload.read(objectMapper, event);
    return TelegramText.heading("🚨", event.getTitle())
        + "\n\n<b>Portfolio/plan:</b> "
        + p.get("portfolioId")
        + "/"
        + p.get("planId")
        + " · <b>revision</b> "
        + p.get("revisionNumber")
        + "\n<b>First failure:</b> "
        + p.get("firstFailureYear")
        + " (age "
        + p.get("firstFailureAge")
        + ")"
        + "\n<b>Unfunded:</b> "
        + p.get("totalUnfundedAmount")
        + " · <b>minimum liquid assets:</b> "
        + p.get("minimumLiquidAssets")
        + "\n<b>Limit:</b> "
        + p.get("limitingCondition")
        + "\n\n"
        + TelegramText.link(
            "Open analysis",
            links.link(
                "/portfolios/" + p.get("portfolioId") + "/analysis?planId=" + p.get("planId")));
  }
}
