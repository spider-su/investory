package com.smartbox.investory.integrations.management.application.handler;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobContext;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandler;
import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.telegram.TelegramIntegrationPlugin;
import com.smartbox.investory.integrations.telegram.TelegramNotificationDeliveryChannel;
import com.smartbox.investory.longterm.api.LongTermAssetPaymentAuditReader;
import com.smartbox.investory.longterm.api.LongTermAssetPaymentAuditReader.FeeReviewRow;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Monthly manual review of stored fees before paying current utility and admin bills. */
@Component
@RequiredArgsConstructor
public class RentalFeeReviewJobHandler implements IntegrationJobHandler {
  private static final int MAX_MESSAGE_HTML_LENGTH = 3_500;
  private final IntegrationConfigurationService configurationService;
  private final TelegramNotificationDeliveryChannel notificationDelivery;
  private final LongTermAssetPaymentAuditReader paymentAudit;

  public IntegrationType integrationType() {
    return IntegrationType.NOTIFICATION;
  }

  public String jobType() {
    return TelegramIntegrationPlugin.RENTAL_FEE_REVIEW_JOB;
  }

  public void execute(IntegrationJobContext context) {
    String configuredPortfolioId =
        configurationService.resolve(context.instance()).value("portfolioId").orElse("");
    long portfolioId;
    try {
      portfolioId = Long.parseLong(configuredPortfolioId);
      if (portfolioId < 1) throw new NumberFormatException();
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Telegram portfolioId must be a positive integer", exception);
    }
    var date =
        context.now().withZoneSameInstant(ZoneId.of(context.job().getTimezone())).toLocalDate();
    notificationDelivery.sendRentalPaymentAudit(render(paymentAudit.feeReview(portfolioId, date)));
  }

  static String render(List<FeeReviewRow> rows) {
    String heading =
        "<b>Rental payments · fee review</b>\nCheck stored monthly amounts against current bills before payment.";
    if (rows.isEmpty()) return heading + "\nNo active rental contracts found.";
    StringBuilder message = new StringBuilder(heading);
    for (var row :
        rows.stream()
            .sorted(Comparator.comparing(FeeReviewRow::assetName, String.CASE_INSENSITIVE_ORDER))
            .toList()) {
      String currency = row.currency() == CurrencyType.PLN ? "" : " · " + row.currency();
      String block =
          "\n\n<b>"
              + TelegramText.escape(row.assetName())
              + currency
              + "</b>"
              + "\nUtilities (media): "
              + format(row.monthlyUtilities())
              + "\nAdmin fees: "
              + format(row.monthlyAdminFee());
      if (message.length() + block.length() > MAX_MESSAGE_HTML_LENGTH) {
        message.append("\n\n…");
        break;
      }
      message.append(block);
    }
    return message.toString();
  }

  private static String format(BigDecimal amount) {
    if (amount == null) return "not set";
    return String.format(
        Locale.US, amount.stripTrailingZeros().scale() <= 0 ? "%,.0f" : "%,.2f", amount);
  }
}
