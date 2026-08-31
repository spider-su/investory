package com.smartbox.investory.services.notifications;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
public class NotificationProperties {

  @Value("${app.notifications.enabled:true}")
  private boolean enabled;

  @Value("${app.notifications.drawdown-threshold-pct:10}")
  private double drawdownThresholdPct;

  @Value("${app.notifications.drawdown-cooldown-hours:24}")
  private long drawdownCooldownHours;

  @Value("${app.notifications.concentration-threshold-pct:25}")
  private double concentrationThresholdPct;

  @Value("${app.notifications.stale-import-days:7}")
  private int staleImportDays;
}
