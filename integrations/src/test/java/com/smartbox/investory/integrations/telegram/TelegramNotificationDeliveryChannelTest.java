package com.smartbox.investory.integrations.telegram;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("Telegram Notification Delivery Channel")
class TelegramNotificationDeliveryChannelTest {
  @DisplayName("successful Telegram Adapter Waits For Confirmed Send")
  @Test
  void successfulTelegramAdapterWaitsForConfirmedSend() {
    IntegrationConfigurationService configuration =
        Mockito.mock(IntegrationConfigurationService.class);
    TelegramApiClient telegram = Mockito.mock(TelegramApiClient.class);
    when(configuration.resolveForRuntime(
            IntegrationType.NOTIFICATION, TelegramIntegrationPlugin.ID, PluginConfig.empty()))
        .thenReturn(new PluginConfig(Map.of("botToken", "token", "chatId", "chat")));

    new TelegramNotificationDeliveryChannel(configuration, telegram).send("message");

    verify(telegram).send("token", "chat", "message");
  }
}
