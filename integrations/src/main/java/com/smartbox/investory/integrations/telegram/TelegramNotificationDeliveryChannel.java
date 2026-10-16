package com.smartbox.investory.integrations.telegram;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.notifications.application.NotificationDeliveryChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramNotificationDeliveryChannel implements NotificationDeliveryChannel {
  private final IntegrationConfigurationService configurationService;
  private final TelegramApiClient telegram;

  @Value("${app.telegram.rental-payment-audit-chat-id:}")
  private String rentalPaymentAuditChatId = "";

  @Override
  public void send(String message) {
    sendToConfiguredChat(message, null);
  }

  public void sendRentalPaymentAudit(String message) {
    sendToConfiguredChat(message, rentalPaymentAuditChatId);
  }

  private void sendToConfiguredChat(String message, String destinationOverride) {
    PluginConfig config =
        configurationService.resolveForRuntime(
            IntegrationType.NOTIFICATION, TelegramIntegrationPlugin.ID, PluginConfig.empty());
    String token = config.value("botToken").orElse("");
    String chatId =
        destinationOverride == null || destinationOverride.isBlank()
            ? config.value("chatId").orElse("")
            : destinationOverride.trim();
    if (token.isBlank() || chatId.isBlank()) return;
    telegram.send(token, chatId, message);
  }
}
