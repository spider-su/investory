package com.smartbox.investory.integrations.notifications;

import static org.mockito.Mockito.verify;

import com.smartbox.investory.integrations.bot.PortfolioBot;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TelegramNotificationDeliveryChannelTest {
  @Test
  void successfulTelegramAdapterWaitsForConfirmedSend() {
    PortfolioBot bot = Mockito.mock(PortfolioBot.class);

    new TelegramNotificationDeliveryChannel(bot).send("message");

    verify(bot).sendMessageConfirmed("message");
  }
}
