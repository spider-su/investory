package com.example.demo.services.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
public class NotificationProperties {

    @Value("${app.notifications.enabled:true}")
    private boolean enabled;

    @Value("${app.notifications.drawdown-threshold-pct:10}")
    private double drawdownThresholdPct;

    @Value("${app.notifications.big-move-threshold-pct:5}")
    private double bigMoveThresholdPct;

    @Value("${app.notifications.concentration-threshold-pct:25}")
    private double concentrationThresholdPct;

    @Value("${app.notifications.stale-import-days:7}")
    private int staleImportDays;
}

