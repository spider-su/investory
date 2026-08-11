package com.example.demo.config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.example.demo.services.MarketService;
import com.example.demo.services.currency.CurrencyRateUpdaterService;
import com.example.demo.services.notifications.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulerConfigTest {

  @Mock private MarketService marketService;
  @Mock private CurrencyRateUpdaterService updaterService;
  @Mock private NotificationService notificationService;

  @InjectMocks private SchedulerConfig schedulerConfig;

  @Test
  void updateCurrencyRates_delegatesToUpdaterService() {
    schedulerConfig.updateCurrencyRates();
    verify(updaterService).updateCurrencyRates();
  }

  @Test
  void recordAtMarketClose_runsFullPortfolioUpdate() {
    schedulerConfig.recordAtMarketClose();

    org.mockito.InOrder order = inOrder(marketService);
    order.verify(marketService).fullPortfolioUpdate();
  }

  @Test
  void sendNotifications_delegatesToNotificationService() {
    schedulerConfig.sendNotifications();
    verify(notificationService).sendDailyDigest();
    verify(notificationService).runAlerts();
  }
}
