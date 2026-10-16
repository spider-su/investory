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
      table
          .append(pad(row.assetName(), propertyWidth))
          .append("  ")
          .append(pad(row.tenantName(), tenantWidth))
          .append("  ")
          .append(leftPad(amount, amountWidth))
          .append('\n');
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
    return value.codePointCount(0, value.length());
  }

  private static String clipped(String value, int width) {
    String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    if (displayWidth(safe) <= width) return safe;
    return safe.substring(0, safe.offsetByCodePoints(0, Math.max(0, width - 1))) + "…";
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
