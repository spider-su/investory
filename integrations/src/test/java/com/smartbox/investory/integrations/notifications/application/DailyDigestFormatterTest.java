package com.smartbox.investory.integrations.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DailyDigestFormatterTest {
  private final DailyDigestFormatter formatter = new DailyDigestFormatter(new ObjectMapper());

  @Test
  void missingDailyValuesShowUnavailableAndKeepIndependentMovers() {
    var payload = payload("day", "", "2026-10-21");
    payload.put("movers", "APLD.US +8.5%");
    String message = format(payload);
    assertThat(message)
        .contains("Portfolio 21 Oct", "Result unavailable", "2026-10-21", "APLD.US +8.5%")
        .doesNotContain("$0", "today", "are dividends");
  }

  @Test
  void missingWeeklyValuesDoNotShowPartialProfitOrDividendTotals() {
    String message = format(payload("week", "", "2026-10-15, 2026-10-16"));
    assertThat(message)
        .contains("Portfolio Weekly", "Result unavailable", "2026-10-15, 2026-10-16")
        .doesNotContain("Last week:", "Dividends", "$0");
  }

  @Test
  void genuineZeroStillShowsZeroForTheDatedReport() {
    assertThat(format(payload("day", "+0", "")))
        .contains("Portfolio 21 Oct", "Result: +$0")
        .doesNotContain("unavailable", "today");
  }

  @Test
  void existingPayloadWithoutAvailabilityFieldStillFormats() {
    var payload = payload("day", "+125", "");
    payload.remove("missingDates");
    assertThat(format(payload)).contains("Result: +$125");
  }

  private String format(Map<String, String> payload) {
    var event = new NotificationEventEntity();
    event.setPayload(payload);
    return formatter.format(event);
  }

  private Map<String, String> payload(String period, String profit, String missingDates) {
    return new HashMap<>(
        Map.of(
            "period",
            period,
            "date",
            "2026-10-21",
            "currency",
            "USD",
            "balance",
            "199,644",
            "profit",
            profit,
            "dividends",
            "+0",
            "missingDates",
            missingDates));
  }
}
