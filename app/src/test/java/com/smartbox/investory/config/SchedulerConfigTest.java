package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.smartbox.investory.integrations.notifications.application.NotificationService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.valuation.price.MarketDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
@DisplayName("Scheduler Config")
class SchedulerConfigTest {

  @Mock private MarketDataService marketDataService;
  @Mock private CurrencyRateUpdaterService updaterService;
  @Mock private NotificationService notificationService;

  @InjectMocks private SchedulerConfig schedulerConfig;

  @DisplayName("scheduling Disabled does Not Register Scheduler Configuration")
  @Test
  void schedulingDisabled_doesNotRegisterSchedulerConfiguration() {
    new ApplicationContextRunner()
        .withPropertyValues("app.scheduling.enabled=false")
        .withUserConfiguration(SchedulerConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(SchedulerConfig.class));
  }

  @DisplayName("update Currency Rates delegates To Updater Service")
  @Test
  void updateCurrencyRates_delegatesToUpdaterService() {
    schedulerConfig.updateCurrencyRates();
    verify(updaterService).updateCurrencyRates();
  }

  @DisplayName("record At Market Close runs Full Portfolio Update")
  @Test
  void recordAtMarketClose_runsFullPortfolioUpdate() {
    schedulerConfig.recordAtMarketClose();

    org.mockito.InOrder order = inOrder(marketDataService);
    order.verify(marketDataService).fullPortfolioUpdate();
  }

  @DisplayName("send Notifications delegates To Notification Service")
  @Test
  void sendNotifications_delegatesToNotificationService() {
    schedulerConfig.sendNotifications();
    verify(notificationService).sendDailyDigest();
    verify(notificationService).runAlerts();
  }
}
