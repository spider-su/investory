package com.example.demo.config;

import com.example.demo.services.CurrencyRateUpdaterService;
import com.example.demo.services.MarketService;
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

    @Scheduled(cron = "0 0 6 * * 1-5") // Every day at 6 AM
    public void updateCurrencyRates() {
        updaterService.updateCurrencyRates();
    }

    @Scheduled(cron = "0 30 15 * * 1-5", zone = "Europe/Warsaw")
    public void recordAtMarketOpen() {
//        updaterService.updateCurrencyRates();
        marketService.fullPortfolioUpdate();
    }

    @Scheduled(cron = "0 00 22 * * 1-5", zone = "Europe/Warsaw")
    public void recordAtMarketClose() {
        marketService.fullPortfolioUpdate();
    }
}
