package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ThresholdAlertFormatter implements NotificationMessageFormatter {
  private static final Pattern DRAWDOWN =
      Pattern.compile(
          "Drawdown alert: ([0-9.]+)% below peak \\(peak ([0-9,.]+) ([A-Z]{3}), now ([0-9,.]+) \\3\\)");

  private final ObjectMapper objectMapper;

  @Override
  public NotificationEventType type() {
    return NotificationEventType.THRESHOLD_ALERT;
  }

  @Override
  public String format(NotificationEventEntity event) {
    Map<String, String> payload = NotificationPayload.read(objectMapper, event);
    String message = payload.getOrDefault("message", event.getTitle());
    Matcher drawdown = DRAWDOWN.matcher(message);
    if (drawdown.matches()) {
      String currency = drawdown.group(3);
      double peak = parse(drawdown.group(2));
      double current = parse(drawdown.group(4));
      return TelegramText.heading("⚠️", "Portfolio drawdown")
          + "\n\n<b>−"
          + TelegramText.escape(drawdown.group(1))
          + "%</b> from previous peak"
          + "\n\nCurrent   <b>"
          + TelegramText.escape(money(current, currency))
          + "</b>"
          + "\nPeak      "
          + TelegramText.escape(money(peak, currency))
          + "\nDifference "
          + TelegramText.escape(money(current - peak, currency));
    }
    return TelegramText.heading("⚠️", event.getTitle()) + "\n\n" + TelegramText.escape(message);
  }

  private static double parse(String value) {
    return Double.parseDouble(value.replace(",", ""));
  }

  private static String money(double value, String currency) {
    String digits = String.format(Locale.US, "%,.0f", Math.abs(value));
    String sign = value < 0 ? "−" : "";
    return switch (currency) {
      case "USD" -> sign + "$" + digits;
      case "EUR" -> sign + "€" + digits;
      case "PLN" -> sign + digits + " zł";
      default -> sign + digits + " " + currency;
    };
  }
}
