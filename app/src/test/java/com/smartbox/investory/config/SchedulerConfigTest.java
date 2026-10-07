package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.smartbox.investory.integrations.notifications.application.NotificationEventDispatcher;
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

  @Mock private NotificationEventDispatcher notificationEventDispatcher;

  @InjectMocks private SchedulerConfig schedulerConfig;

  @DisplayName("scheduling Disabled does Not Register Scheduler Configuration")
  @Test
  void schedulingDisabled_doesNotRegisterSchedulerConfiguration() {
    new ApplicationContextRunner()
        .withPropertyValues("app.scheduling.enabled=false")
        .withUserConfiguration(SchedulerConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(SchedulerConfig.class));
  }

  @DisplayName("dispatches Pending Notification Events")
  @Test
  void dispatchNotificationEvents_delegatesToDispatcher() {
    schedulerConfig.dispatchNotificationEvents();
    verify(notificationEventDispatcher).dispatchPending();
  }
}
