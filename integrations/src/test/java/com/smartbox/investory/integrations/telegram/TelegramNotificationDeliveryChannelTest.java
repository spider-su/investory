package com.smartbox.investory.integrations.telegram;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TelegramNotificationDeliveryChannelTest {
  private final IntegrationConfigurationService configuration = mock();
  private final TelegramApiClient telegram = mock();
  private final TelegramNotificationDeliveryChannel channel =
      new TelegramNotificationDeliveryChannel(configuration, telegram);

  @BeforeEach
  void configureInvestory() {
    when(configuration.resolveForRuntime(
            IntegrationType.NOTIFICATION, TelegramIntegrationPlugin.ID, PluginConfig.empty()))
        .thenReturn(
            new PluginConfig(Map.of("botToken", "investory-token", "chatId", "investory-chat")));
  }

  @Test
  void portfolioMessagesKeepInvestoryCredentials() {
    configureRental();
    var event = event(NotificationEventType.DAILY_DIGEST, "");
    channel.send(event, "portfolio");
    verify(telegram).send("investory-token", "investory-chat", "portfolio");
    verifyNoMoreInteractions(telegram);
  }

  @Test
  void monthlyRentalAuditUsesOnlyRentalBotAndChat() {
    configureRental();
    channel.sendRentalPaymentAudit("audit");
    verify(telegram).send("rental-token", "rental-chat", "audit");
    verifyNoMoreInteractions(telegram);
  }

  @Test
  void rentalContractAlertUsesOnlyRentalBotAndChat() {
    configureRental();
    channel.send(
        event(NotificationEventType.THRESHOLD_ALERT, "RENTAL_CONTRACT_EXPIRING"), "rental alert");
    verify(telegram).send("rental-token", "rental-chat", "rental alert");
    verifyNoMoreInteractions(telegram);
  }

  @Test
  void legacyRentalAlertSourceAlsoRoutesToRentalBot() {
    configureRental();
    var event = event(NotificationEventType.THRESHOLD_ALERT, "");
    event.setSourceEntityId("RENTAL_CONTRACT_EXPIRING:42");
    channel.send(event, "legacy rental");
    verify(telegram).send("rental-token", "rental-chat", "legacy rental");
  }

  @Test
  void investmentAlertKeepsInvestoryBot() {
    configureRental();
    channel.send(event(NotificationEventType.THRESHOLD_ALERT, "DRAWDOWN"), "drawdown");
    verify(telegram).send("investory-token", "investory-chat", "drawdown");
    verifyNoMoreInteractions(telegram);
  }

  @Test
  void missingRentalTokenFailsWithoutSendingToInvestory() {
    ReflectionTestUtils.setField(channel, "rentalPaymentAuditChatId", "rental-chat");
    assertThatThrownBy(() -> channel.sendRentalPaymentAudit("audit"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Rental Telegram");
    verifyNoInteractions(telegram);
  }

  @Test
  void missingRentalChatFailsWithoutUsingInvestoryChat() {
    ReflectionTestUtils.setField(channel, "rentalBotToken", "rental-token");
    assertThatThrownBy(() -> channel.sendRentalPaymentAudit("audit"))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(telegram);
  }

  @Test
  void rentalFailurePropagatesWithoutFallback() {
    configureRental();
    doThrow(new IllegalStateException("rejected"))
        .when(telegram)
        .send("rental-token", "rental-chat", "audit");
    assertThatThrownBy(() -> channel.sendRentalPaymentAudit("audit"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("rejected");
    verify(telegram).send("rental-token", "rental-chat", "audit");
    verifyNoMoreInteractions(telegram);
  }

  @Test
  void disabledIntegrationDoesNotSendThroughEitherBot() {
    configureRental();
    when(configuration.resolveForRuntime(
            IntegrationType.NOTIFICATION, TelegramIntegrationPlugin.ID, PluginConfig.empty()))
        .thenReturn(PluginConfig.empty());
    channel.send("portfolio");
    channel.sendRentalPaymentAudit("audit");
    verifyNoInteractions(telegram);
  }

  private void configureRental() {
    ReflectionTestUtils.setField(channel, "rentalBotToken", "rental-token");
    ReflectionTestUtils.setField(channel, "rentalPaymentAuditChatId", "rental-chat");
  }

  private NotificationEventEntity event(NotificationEventType type, String rule) {
    var event = new NotificationEventEntity();
    event.setEventType(type);
    event.setPayload(Map.of("rule", rule));
    return event;
  }
}
