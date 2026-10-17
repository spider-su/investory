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
import org.springframework.test.util.ReflectionTestUtils;

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

  @Test
  void rentalAuditFallsBackToRuntimeChatAndOverrideDoesNotAffectOtherMessages() {
    IntegrationConfigurationService configuration =
        Mockito.mock(IntegrationConfigurationService.class);
    TelegramApiClient telegram = Mockito.mock(TelegramApiClient.class);
    when(configuration.resolveForRuntime(
            IntegrationType.NOTIFICATION, TelegramIntegrationPlugin.ID, PluginConfig.empty()))
        .thenReturn(
            new PluginConfig(Map.of("botToken", "runtime-token", "chatId", "runtime-chat")));
    TelegramNotificationDeliveryChannel channel =
        new TelegramNotificationDeliveryChannel(configuration, telegram);

    channel.sendRentalPaymentAudit("audit");
    channel.send("ordinary");

    verify(telegram).send("runtime-token", "runtime-chat", "audit");
    verify(telegram).send("runtime-token", "runtime-chat", "ordinary");
    Mockito.verifyNoMoreInteractions(telegram);
  }

  @Test
  void rentalAuditOverrideSendsOnlyToDedicatedChat() {
    IntegrationConfigurationService configuration =
        Mockito.mock(IntegrationConfigurationService.class);
    TelegramApiClient telegram = Mockito.mock(TelegramApiClient.class);
    when(configuration.resolveForRuntime(
            IntegrationType.NOTIFICATION, TelegramIntegrationPlugin.ID, PluginConfig.empty()))
        .thenReturn(
            new PluginConfig(Map.of("botToken", "runtime-token", "chatId", "runtime-chat")));
    TelegramNotificationDeliveryChannel channel =
        new TelegramNotificationDeliveryChannel(configuration, telegram);
    ReflectionTestUtils.setField(channel, "rentalPaymentAuditChatId", "dedicated-chat");

    channel.sendRentalPaymentAudit("audit");

    verify(telegram).send("runtime-token", "dedicated-chat", "audit");
    Mockito.verifyNoMoreInteractions(telegram);
  }
}
