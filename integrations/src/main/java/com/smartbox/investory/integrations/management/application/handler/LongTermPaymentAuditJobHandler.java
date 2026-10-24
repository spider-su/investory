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
  private static final int MAX_MESSAGE_HTML_LENGTH = 3_500;
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
    String heading = "<b>Rental income · monthly</b>";
    if (rows.isEmpty()) return heading + "\nNo active tenant payments found.";
    StringBuilder message = new StringBuilder(heading);
    for (var row :
        rows.stream()
            .sorted(
                java.util.Comparator.comparing(
                    LongTermAssetPaymentAuditReader.PaymentAuditRow::assetName,
                    String.CASE_INSENSITIVE_ORDER))
            .toList()) {
      String block =
          "\n\n<b>"
              + TelegramText.escape(row.assetName())
              + " · "
              + format(row.monthlyRentalIncome())
              + "</b>\n"
              + TelegramText.escape(row.tenantName());
      if (message.length() + block.length() > MAX_MESSAGE_HTML_LENGTH) {
        message.append("\n\n…");
        break;
      }
      message.append(block);
    }
    return message.toString();
  }

  private static String format(BigDecimal value) {
    return String.format(
        Locale.US, value.stripTrailingZeros().scale() <= 0 ? "%,.0f" : "%,.2f", value);
  }
}
