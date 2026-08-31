package com.smartbox.investory.integrations.telegram;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.notifications.application.NotificationDeliveryChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramNotificationDeliveryChannel implements NotificationDeliveryChannel {
  private final IntegrationConfigurationService configurationService;
  private final TelegramApiClient telegram;

  @Override
  public void send(String message) {
    PluginConfig config =
        configurationService.resolveForRuntime(
            IntegrationType.NOTIFICATION, TelegramIntegrationPlugin.ID, PluginConfig.empty());
    String token = config.value("botToken").orElse("");
    String chatId = config.value("chatId").orElse("");
    if (token.isBlank() || chatId.isBlank()) return;
    telegram.send(token, chatId, message);
  }
}
