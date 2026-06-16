package com.example.demo.config;

import com.example.demo.controllers.bot.PortfolioBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramBotConfig {

    @Autowired
    private PortfolioBot portfolioBot;

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @PostConstruct
    public void registerBot() {
        try {
            telegramBotsApi().registerBot(portfolioBot);
            log.info("Telegram bot registered: {}", portfolioBot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot", e);
        }
    }
}

