package com.smartbox.investory.integrations.notifications;

import com.smartbox.investory.integrations.bot.PortfolioBot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramNotificationDeliveryChannel implements NotificationDeliveryChannel {
  private final PortfolioBot bot;

  @Override
  public void send(String message) {
    bot.sendMessageConfirmed(message);
  }
}
