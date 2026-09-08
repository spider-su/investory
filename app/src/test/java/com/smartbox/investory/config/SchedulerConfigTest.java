package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.smartbox.investory.integrations.notifications.application.NotificationService;
import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
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

  @Mock private InvestmentMaintenanceApi investmentMaintenance;
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

  @DisplayName("update Currency Rates delegates To Investment Maintenance")
  @Test
  void updateCurrencyRates_delegatesToUpdaterService() {
    schedulerConfig.updateCurrencyRates();
    verify(investmentMaintenance).refreshCurrency();
  }

  @DisplayName("record At Market Close delegates To Investment Maintenance")
  @Test
  void recordAtMarketClose_runsFullPortfolioUpdate() {
    schedulerConfig.recordAtMarketClose();

    verify(investmentMaintenance).refreshPrices();
  }

  @DisplayName("send Notifications delegates To Notification Service")
  @Test
  void sendNotifications_delegatesToNotificationService() {
    schedulerConfig.sendNotifications();
    verify(notificationService).sendDailyDigest();
    verify(notificationService).runAlerts();
  }
}
