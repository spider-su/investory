package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import com.smartbox.investory.integrations.notifications.application.NotificationService;
import com.smartbox.investory.integrations.telegram.TelegramIntegrationPlugin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyNotificationsJobHandler implements IntegrationJobHandler {
  private final NotificationService notifications;

  @Override
  public IntegrationType integrationType() {
    return IntegrationType.NOTIFICATION;
  }

  @Override
  public String jobType() {
    return TelegramIntegrationPlugin.DAILY_NOTIFICATIONS_JOB;
  }

  @Override
  public void execute(IntegrationJobContext context) {
    notifications.sendDailyDigest();
    notifications.runAlerts();
  }
}
