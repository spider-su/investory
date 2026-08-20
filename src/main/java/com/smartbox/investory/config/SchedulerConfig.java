package com.smartbox.investory.config;

import com.smartbox.investory.integration.notifications.NotificationService;
import com.smartbox.investory.investment.market.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.market.price.MarketService;
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

  @Scheduled(cron = "0 0 15 * * 1-5", zone = "Europe/Warsaw")
  public void updateCurrencyRates() {
    updaterService.updateCurrencyRates();
  }

  @Scheduled(cron = "0 01 22 * * 1-5", zone = "Europe/Warsaw")
  public void recordAtMarketClose() {
    marketService.fullPortfolioUpdate();
  }

  @Scheduled(cron = "0 22 22 * * 1-5", zone = "Europe/Warsaw")
  public void sendNotifications() {
    notificationService.sendDailyDigest();
    notificationService.runAlerts();
  }
}
