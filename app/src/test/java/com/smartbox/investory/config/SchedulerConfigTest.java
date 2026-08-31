package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.smartbox.investory.integrations.notifications.NotificationService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.valuation.price.MarketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class SchedulerConfigTest {

  @Mock private MarketService marketService;
  @Mock private CurrencyRateUpdaterService updaterService;
  @Mock private NotificationService notificationService;

  @InjectMocks private SchedulerConfig schedulerConfig;

  @Test
  void schedulingDisabled_doesNotRegisterSchedulerConfiguration() {
    new ApplicationContextRunner()
        .withPropertyValues("app.scheduling.enabled=false")
        .withUserConfiguration(SchedulerConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(SchedulerConfig.class));
  }

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
