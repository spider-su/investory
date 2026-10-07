package com.smartbox.investory.config;

import com.smartbox.investory.integrations.notifications.application.NotificationEventDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@RequiredArgsConstructor
public class SchedulerConfig {

  private final NotificationEventDispatcher notificationEventDispatcher;

  @Scheduled(fixedDelayString = "${app.notifications.dispatch.interval-ms:60000}")
  public void dispatchNotificationEvents() {
    notificationEventDispatcher.dispatchPending();
  }
}
