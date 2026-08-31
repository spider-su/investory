package com.smartbox.investory.integrations.notifications.application;

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
    return "🚨 "
        + event.getTitle()
        + "\nPortfolio/plan: "
        + p.get("portfolioId")
        + "/"
        + p.get("planId")
        + " · revision "
        + p.get("revisionNumber")
        + "\nFirst failure: "
        + p.get("firstFailureYear")
        + " (age "
        + p.get("firstFailureAge")
        + ")"
        + "\nUnfunded: "
        + p.get("totalUnfundedAmount")
        + " · minimum liquid assets: "
        + p.get("minimumLiquidAssets")
        + "\nLimit: "
        + p.get("limitingCondition")
        + "\n"
        + links.link(
            "/analysis?portfolioId=" + p.get("portfolioId") + "&planId=" + p.get("planId"));
  }
}
