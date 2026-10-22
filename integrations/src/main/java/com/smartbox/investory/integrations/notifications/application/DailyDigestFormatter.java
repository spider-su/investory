package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class DailyDigestFormatter implements NotificationMessageFormatter {
  private final ObjectMapper objectMapper;

  @Override
  public NotificationEventType type() {
    return NotificationEventType.DAILY_DIGEST;
  }

  @Override
  public String format(NotificationEventEntity event) {
    Map<String, String> p = NotificationPayload.read(objectMapper, event);
    String currency = p.getOrDefault("currency", "");
    if (!p.containsKey("period")) {
      return TelegramText.heading("📊", "Portfolio")
          + "\n\n"
          + TelegramText.escape(p.getOrDefault("message", event.getTitle()));
    }
    LocalDate date = LocalDate.parse(p.get("date"));
    String heading =
        p.get("period").equals("week")
            ? "Portfolio Weekly " + date.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
            : "Portfolio "
                + date.format(DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH));
    String result = TelegramText.heading("📊", heading);
    String missingDates = p.getOrDefault("missingDates", "");
    if (!missingDates.isBlank()) {
      String message =
          result
              + "\nResult unavailable: missing daily valuations for "
              + TelegramText.escape(missingDates);
      String movers = p.getOrDefault("movers", "");
      return movers.isBlank() ? message : message + "\n" + TelegramText.escape(movers);
    }
    if (p.get("period").equals("week")) {
      return result
          + "\n💰  Total: <b>"
          + TelegramText.escape(money(p.get("balance"), currency))
          + "</b>\n📈  Last week: <b>"
          + TelegramText.escape(money(p.get("profit"), currency))
          + "</b>\n💵  Dividends "
          + TelegramText.escape(money(p.get("dividends"), currency));
    }
    String message = result + "\nResult: " + TelegramText.escape(money(p.get("profit"), currency));
    if (!isZero(p.get("dividends"))) {
      message +=
          " (" + TelegramText.escape(money(p.get("dividends"), currency)) + " are dividends)";
    }
    String movers = p.getOrDefault("movers", "");
    if (!movers.isBlank()) {
      message += "\n" + TelegramText.escape(movers);
    }
    return message;
  }

  private static boolean isZero(String amount) {
    return amount == null
        || amount.replaceAll("[^0-9]", "").isBlank()
        || amount.replaceAll("[^0-9]", "").chars().allMatch(c -> c == '0');
  }

  private static String money(String value, String currency) {
    String sign = "";
    String digits = value;
    if (value.startsWith("+") || value.startsWith("−") || value.startsWith("-")) {
      sign = value.substring(0, 1).replace("-", "−");
      digits = value.substring(1);
    }
    return switch (currency) {
      case "USD" -> sign + "$" + digits;
      case "EUR" -> sign + "€" + digits;
      case "PLN" -> sign + digits + " zł";
      default -> sign + digits + (currency.isBlank() ? "" : " " + currency);
    };
  }
}
