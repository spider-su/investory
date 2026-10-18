package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.telegram.TelegramNotificationDeliveryChannel;
import com.smartbox.investory.longterm.api.LongTermAssetPaymentAuditReader;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LongTermPaymentAuditJobHandler implements IntegrationJobHandler {
  private static final int MAX_TABLE_HTML_LENGTH = 3_500;
  private final IntegrationConfigurationService configurationService;
  private final TelegramNotificationDeliveryChannel notificationDelivery;
  private final LongTermAssetPaymentAuditReader paymentAudit;

  public IntegrationType integrationType() {
    return IntegrationType.NOTIFICATION;
  }

  public String jobType() {
    return "audit-long-term-payments";
  }

  public void execute(IntegrationJobContext context) {
    String configuredPortfolioId =
        configurationService.resolve(context.instance()).value("portfolioId").orElse("");
    if (configuredPortfolioId.isBlank())
      throw new IllegalArgumentException("Telegram portfolioId is required for payment audit");
    long portfolioId;
    try {
      portfolioId = Long.parseLong(configuredPortfolioId);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Telegram portfolioId must be a positive integer", exception);
    }
    var rows =
        paymentAudit.paymentAudit(
            portfolioId,
            context
                .now()
                .withZoneSameInstant(ZoneId.of(context.job().getTimezone()))
                .toLocalDate());
    notificationDelivery.sendRentalPaymentAudit(render(rows));
  }

  static String render(java.util.List<LongTermAssetPaymentAuditReader.PaymentAuditRow> rows) {
    if (rows.isEmpty()) return "<b>Rental payments · review</b>\nNo active tenant payments found.";
    int propertyWidth = width(rows.stream().map(row -> row.assetName()).toList(), 14, 20);
    int tenantWidth = width(rows.stream().map(row -> row.tenantName()).toList(), 10, 18);
    int amountWidth =
        Math.max(
            6,
            rows.stream()
                .map(row -> format(row.totalMonthlyPayment()) + " " + row.currency())
                .mapToInt(LongTermPaymentAuditJobHandler::displayWidth)
                .max()
                .orElse(6));
    StringBuilder table = new StringBuilder();
    table
        .append(pad("Property", propertyWidth))
        .append("  ")
        .append(pad("Tenant", tenantWidth))
        .append("  ")
        .append(leftPad("Amount", amountWidth))
        .append('\n');
    for (var row : rows) {
      String amount = format(row.totalMonthlyPayment()) + " " + row.currency();
      String line =
          pad(row.assetName(), propertyWidth)
              + "  "
              + pad(row.tenantName(), tenantWidth)
              + "  "
              + leftPad(amount, amountWidth);
      if (TelegramText.escape(table + line).length() > MAX_TABLE_HTML_LENGTH) {
        table.append("…");
        break;
      }
      table.append(line).append('\n');
    }
    return "<b>Rental payments · review</b>\n<pre>"
        + TelegramText.escape(table.toString().stripTrailing())
        + "</pre>";
  }

  private static int width(java.util.List<String> values, int minimum, int maximum) {
    return Math.max(
        minimum,
        Math.min(
            maximum,
            values.stream()
                .mapToInt(LongTermPaymentAuditJobHandler::displayWidth)
                .max()
                .orElse(0)));
  }

  private static int displayWidth(String value) {
    int width = 0;
    for (int i = 0; i < value.length(); ) {
      int cp = value.codePointAt(i);
      i += Character.charCount(cp);
      int type = Character.getType(cp);
      if (type == Character.NON_SPACING_MARK
          || type == Character.COMBINING_SPACING_MARK
          || type == Character.ENCLOSING_MARK
          || cp == 0x200D
          || cp == 0xFE0F) continue;
      width += isWide(cp) ? 2 : 1;
    }
    return width;
  }

  private static boolean isWide(int cp) {
    return cp >= 0x1100
        && (cp <= 0x115F
            || cp == 0x2329
            || cp == 0x232A
            || cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F
            || cp >= 0xAC00 && cp <= 0xD7A3
            || cp >= 0xF900 && cp <= 0xFAFF
            || cp >= 0xFE10 && cp <= 0xFE19
            || cp >= 0xFE30 && cp <= 0xFE6F
            || cp >= 0xFF00 && cp <= 0xFF60
            || cp >= 0xFFE0 && cp <= 0xFFE6
            || cp >= 0x1F300 && cp <= 0x1FAFF
            || cp >= 0x20000 && cp <= 0x3FFFD);
  }

  private static String clipped(String value, int width) {
    String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    if (displayWidth(safe) <= width) return safe;
    StringBuilder clipped = new StringBuilder();
    int used = 0;
    for (int i = 0; i < safe.length(); ) {
      int cp = safe.codePointAt(i);
      int cells = displayWidth(new String(Character.toChars(cp)));
      if (used + cells > Math.max(0, width - 1)) break;
      clipped.appendCodePoint(cp);
      used += cells;
      i += Character.charCount(cp);
    }
    return clipped + "…";
  }

  private static String pad(String value, int width) {
    String clipped = clipped(value, width);
    return clipped + " ".repeat(Math.max(0, width - displayWidth(clipped)));
  }

  private static String leftPad(String value, int width) {
    String clipped = clipped(value, width);
    return " ".repeat(Math.max(0, width - displayWidth(clipped))) + clipped;
  }

  private static String format(BigDecimal value) {
    return String.format(Locale.US, "%,.2f", value);
  }
}
