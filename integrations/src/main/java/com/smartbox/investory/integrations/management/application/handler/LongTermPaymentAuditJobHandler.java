package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import com.smartbox.investory.integrations.notifications.application.NotificationDeliveryChannel;
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
  private final NotificationDeliveryChannel notificationDelivery;
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
    StringBuilder message = new StringBuilder("Please check payments for the following tenants:");
    for (var row : rows) {
      message
          .append("\n")
          .append(row.assetName())
          .append(" | ")
          .append(row.tenantName())
          .append(" | ")
          .append(format(row.totalMonthlyPayment()))
          .append(" ")
          .append(row.currency());
    }
    if (rows.isEmpty()) message.append("\nNo active tenant payments found.");
    notificationDelivery.send(message.toString());
  }

  private static String format(BigDecimal value) {
    return String.format(Locale.US, "%,.2f", value);
  }
}
