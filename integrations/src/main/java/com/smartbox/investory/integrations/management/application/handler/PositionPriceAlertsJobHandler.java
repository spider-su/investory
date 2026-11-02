package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import com.smartbox.investory.integrations.notifications.application.NotificationProperties;
import com.smartbox.investory.integrations.notifications.application.PositionPriceAlertService;
import com.smartbox.investory.integrations.telegram.TelegramIntegrationPlugin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Evaluates the most recent canonical quotes at 15:00 Europe/Warsaw. */
@Component
@RequiredArgsConstructor
public class PositionPriceAlertsJobHandler implements IntegrationJobHandler {
  private final PositionPriceAlertService alerts;
  private final NotificationProperties configuration;

  @Override
  public IntegrationType integrationType() {
    return IntegrationType.NOTIFICATION;
  }

  @Override
  public String jobType() {
    return TelegramIntegrationPlugin.POSITION_PRICE_ALERTS_JOB;
  }

  @Override
  public void execute(IntegrationJobContext context) {
    if (configuration.isEnabled() && configuration.getPortfolioId() != null) {
      alerts.evaluate(configuration.getPortfolioId());
    }
  }
}
