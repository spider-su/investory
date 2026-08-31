package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader;
import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader.PortfolioOperationsSnapshot;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Aggregates and dispatches portfolio notifications: - {@link #sendDailyDigest()} pushes a snapshot
 * summary after the market-close job. - {@link #runAlerts()} evaluates every {@link AlertRule} and
 * sends only fired ones.
 *
 * <p>Delivery adapters are optional. When none are enabled, messages are logged instead of sent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final ObjectProvider<NotificationDeliveryChannel> channelProvider;
  private final PortfolioOperationsReader investment;
  private final List<AlertRule> alertRules;
  private final NotificationProperties properties;

  public void sendDailyDigest() {
    if (!properties.isEnabled()) {
      return;
    }
    try {
      PortfolioOperationsSnapshot p = investment.portfolio();
      String message = buildDigest(p);
      send(message);
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
        rule.evaluate()
            .ifPresent(
                message -> {
                  log.info("Alert fired: {}", rule.code());
                  send("\u26A0\uFE0F " + message);
                });
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

  private void send(String message) {
    List<NotificationDeliveryChannel> channels = channelProvider.orderedStream().toList();
    if (channels.isEmpty()) {
      log.info("[notification] {}", message.replace('\n', ' '));
      return;
    }
    channels.forEach(channel -> channel.send(message));
  }
}
