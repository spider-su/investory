package com.example.demo.config;

import com.example.demo.services.currency.CurrencyRateUpdaterService;
import com.example.demo.services.MarketService;
import com.example.demo.services.notifications.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulerConfig {

    private final MarketService marketService;
    private final CurrencyRateUpdaterService updaterService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 15 * * 1-5")
    public void updateCurrencyRates() {
        updaterService.updateCurrencyRates();
    }

    @Scheduled(cron = "0 31 15 * * 1-5", zone = "Europe/Warsaw")
    public void recordAtMarketOpen() {
        marketService.fullPortfolioUpdate();
    }

    @Scheduled(cron = "0 01 22 * * 1-5", zone = "Europe/Warsaw")
    public void recordAtMarketClose() {
        marketService.fullPortfolioUpdate();
    }

    @Scheduled(cron = "0 22 22 * * 1-5")
    public void sendNotifications() {
        notificationService.sendDailyDigest();
        notificationService.runAlerts();
    }
}
