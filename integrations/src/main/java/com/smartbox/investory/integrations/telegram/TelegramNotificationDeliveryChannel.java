package com.smartbox.investory.integrations.telegram;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.notifications.application.NotificationDeliveryChannel;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramNotificationDeliveryChannel implements NotificationDeliveryChannel {
  private final IntegrationConfigurationService configurationService;
  private final TelegramApiClient telegram;

  @Value("${app.telegram.rental-bot-token:}")
  private String rentalBotToken = "";

  @Value("${app.telegram.rental-payment-audit-chat-id:}")
  private String rentalPaymentAuditChatId = "";

  @Override
  public void send(NotificationEventEntity event, String message) {
    String rule = event.getPayload() == null ? "" : event.getPayload().getOrDefault("rule", "");
    String source = event.getSourceEntityId() == null ? "" : event.getSourceEntityId();
    if (event.getEventType() == NotificationEventType.THRESHOLD_ALERT
        && ("RENTAL_CONTRACT_EXPIRING".equals(rule)
            || source.equals("RENTAL_CONTRACT_EXPIRING")
            || source.startsWith("RENTAL_CONTRACT_EXPIRING:"))) {
      sendRentalPaymentAudit(message);
    } else {
      send(message);
    }
  }

  @Override
  public void send(String message) {
    PluginConfig config = runtimeConfig();
    String token = config.value("botToken").orElse("");
    String chatId = config.value("chatId").orElse("");
    if (token.isBlank() || chatId.isBlank()) return;
    telegram.send(token, chatId, message);
  }

  public void sendRentalPaymentAudit(String message) {
    if (runtimeConfig().values().isEmpty()) return;
    if (rentalBotToken.isBlank() || rentalPaymentAuditChatId.isBlank()) {
      throw new IllegalStateException("Rental Telegram bot token and chat ID are required");
    }
    telegram.send(rentalBotToken.trim(), rentalPaymentAuditChatId.trim(), message);
  }

  private PluginConfig runtimeConfig() {
    return configurationService.resolveForRuntime(
        IntegrationType.NOTIFICATION, TelegramIntegrationPlugin.ID, PluginConfig.empty());
  }
}
