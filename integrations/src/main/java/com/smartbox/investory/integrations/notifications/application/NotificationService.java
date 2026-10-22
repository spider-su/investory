package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader;
import com.smartbox.investory.investment.api.operations.PortfolioOperationsReader.PortfolioOperationsSnapshot;
import com.smartbox.investory.investment.api.reporting.InvestmentDailyPerformanceApi;
import com.smartbox.investory.investment.api.reporting.model.DailyPerformanceDetail;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
  private final InvestmentDailyPerformanceApi dailyPerformance;
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
      PortfolioOperationsSnapshot p = investment.portfolio(properties.getPortfolioId());
      LocalDate day = clock.instant().atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate();
      boolean weekly = day.getDayOfWeek() == java.time.DayOfWeek.MONDAY;
      LocalDate end = weekly ? day.minusDays(3) : day.minusDays(1);
      LocalDate start = weekly ? end.with(java.time.DayOfWeek.MONDAY) : end;
      List<String> missingDates = new ArrayList<>();
      double profit = 0;
      double dividends = 0;
      for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
        DailyPerformanceDetail detail =
            dailyPerformance.load(properties.getPortfolioId(), date, Set.of());
        if (detail == null || detail.accounts() == null || detail.accounts().isEmpty()) {
          missingDates.add(date.toString());
        } else {
          profit += detail.dailyProfit();
          dividends += detail.dividends();
        }
      }
      String movers =
          weekly
              ? ""
              : dailyPerformance.movers(properties.getPortfolioId(), end, 5, 5).stream()
                  .map(move -> move.symbol() + " " + signedPct(move.changePct()))
                  .collect(java.util.stream.Collectors.joining("\n"));
      publisher.publish(
          new NotificationCandidate(
              NotificationEventType.DAILY_DIGEST,
              NotificationSeverity.WARNING,
              null,
              "DAILY_DIGEST",
              end.toString(),
              (weekly ? "WEEKLY_DIGEST:" : "DAILY_DIGEST:") + end,
              weekly ? "Weekly portfolio recap" : "Daily portfolio recap",
              Map.of(
                  "balance",
                  fmt(p.balance().doubleValue()),
                  "profit",
                  missingDates.isEmpty() ? signed(profit) : "",
                  "dividends",
                  missingDates.isEmpty() ? signed(dividends) : "",
                  "missingDates",
                  String.join(", ", missingDates),
                  "period",
                  weekly ? "week" : "day",
                  "date",
                  (weekly ? day : end).toString(),
                  "movers",
                  movers,
                  "currency",
                  p.baseCurrency()),
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

  private static String signed(double value) {
    String digits = fmt(Math.abs(value));
    return value < 0 ? "−" + digits : "+" + digits;
  }

  private static String fmt(double value) {
    return String.format(Locale.US, "%,.0f", value);
  }

  private static String signedPct(double value) {
    return String.format(Locale.US, "%+.1f%%", value);
  }
}
