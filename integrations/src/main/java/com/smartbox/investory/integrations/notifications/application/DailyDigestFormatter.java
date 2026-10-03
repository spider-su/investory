package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
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
    if (!p.containsKey("balance")) {
      return TelegramText.heading("📊", "Portfolio · Daily")
          + "\n\n"
          + TelegramText.escape(p.getOrDefault("message", event.getTitle()));
    }
    return TelegramText.heading("📊", "Portfolio · Daily")
        + "\n\n💰 <b>"
        + TelegramText.escape(money(p.get("balance"), currency))
        + "</b>  Portfolio value"
        + "\n\n📈 P/L  <b>"
        + TelegramText.escape(money(p.get("profit"), currency))
        + "</b>"
        + "\n├ Realized   "
        + TelegramText.escape(money(p.get("realized"), currency))
        + "\n├ Unrealized "
        + TelegramText.escape(money(p.get("unrealized"), currency))
        + "\n└ Dividends  "
        + TelegramText.escape(money(p.get("dividends"), currency))
        + "\n\n🧾 Tax estimate  "
        + TelegramText.escape(money(p.get("tax"), currency));
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
