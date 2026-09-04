package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader;
import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader.PortfolioOperationsSnapshot;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Evaluates scheduled notification producers and persists their channel-neutral candidates.
 *
 * <p>Delivery is handled separately by {@link NotificationEventDispatcher}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final PortfolioOperationsReader investment;
  private final List<AlertRule> alertRules;
  private final NotificationProperties properties;
  private final NotificationEventPublisher publisher;
  private final AlertRuleTransactionRunner alertRuleTransactionRunner;
  private final Clock clock;

  public void sendDailyDigest() {
    if (!properties.isEnabled()) {
      return;
    }
    try {
      PortfolioOperationsSnapshot p = investment.portfolio();
      String message = buildDigest(p);
      LocalDate day = clock.instant().atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate();
      publisher.publish(
          new NotificationCandidate(
              NotificationEventType.DAILY_DIGEST,
              NotificationSeverity.WARNING,
              null,
              "DAILY_DIGEST",
              day.toString(),
              "DAILY_DIGEST:" + day,
              "Daily digest",
              Map.of("message", message),
              clock.instant()));
    } catch (Exception e) {
      log.warn("Failed to build/send daily digest", e);
    }
  }

  public void runAlerts() {
    if (!properties.isEnabled()) {
      return;
    }
    for (AlertRule rule : alertRules) {
      try {
        alertRuleTransactionRunner.run(rule);
      } catch (Exception e) {
        log.warn("Alert rule {} failed", rule.code(), e);
      }
    }
  }

  private String buildDigest(PortfolioOperationsSnapshot p) {
    return String.format(
        "\uD83D\uDCCA Daily digest%n"
            + "Balance: %s %s%n"
            + "Total P/L: %s %s (unrealized %s, realized %s)%n"
            + "Dividends: %s %s%n"
            + "Cap-gains tax (est): %s %s",
        fmt(p.balance().doubleValue()),
        p.baseCurrency(),
        fmt(p.totalProfit().doubleValue()),
        p.baseCurrency(),
        fmt(p.unrealizedProfit().doubleValue()),
        fmt(p.realizedProfit().doubleValue()),
        fmt(p.dividends().doubleValue()),
        p.baseCurrency(),
        fmt(p.capitalGainsTax().doubleValue()),
        p.baseCurrency());
  }

  private static String fmt(double value) {
    return String.format(Locale.US, "%,.0f", value);
  }
}
