package com.smartbox.investory.config;

import com.smartbox.investory.integrations.telegram.PortfolioBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Registers {@link PortfolioBot} with the Telegram long-polling runtime when {@code
 * app.telegram.enabled=true}. When the flag is off, neither this class nor {@link PortfolioBot} are
 * instantiated, so the app starts cleanly without any Telegram credentials. Uses the telegrambots
 * 6.x API; the transitives (Jackson, OkHttp, Slf4j) are Jakarta-friendly and run on Spring
 * Framework 7.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramBotConfig {

  @Bean
  public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
    return new TelegramBotsApi(DefaultBotSession.class);
  }

  @Bean
  public SmartInitializingSingleton telegramBotRegistration(
      TelegramBotsApi telegramBotsApi, PortfolioBot portfolioBot) {
    return () -> {
      try {
        telegramBotsApi.registerBot(portfolioBot);
        log.info("Telegram bot registered: {}", portfolioBot.getBotUsername());
      } catch (TelegramApiException e) {
        throw new IllegalStateException("Failed to register Telegram bot", e);
      }
    };
  }
}
