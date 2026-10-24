package com.smartbox.investory.integrations.management.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.LongTermAssetPaymentAuditReader.PaymentAuditRow;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LongTermPaymentAuditJobHandlerTest {
  @Test
  void rendersRentalIncomeInReadableEscapedPropertyBlocksWithoutCurrency() {
    String html =
        LongTermPaymentAuditJobHandler.render(
            List.of(
                new PaymentAuditRow(
                    "Reduta 26B/44",
                    "Tenant",
                    new BigDecimal("3780"),
                    new BigDecimal("3610"),
                    CurrencyType.PLN),
                new PaymentAuditRow(
                    "A&B/一",
                    "Żółć <x>",
                    new BigDecimal("3350"),
                    new BigDecimal("3200.50"),
                    CurrencyType.PLN)));

    assertThat(html)
        .isEqualTo(
            "<b>Rental income · monthly</b>\n\n"
                + "<b>A&amp;B/一 · 3,200.50</b>\nŻółć &lt;x&gt;\n\n"
                + "<b>Reduta 26B/44 · 3,610</b>\nTenant");
    assertThat(html).doesNotContain("PLN", "3,780", "3,350", "<pre>");
  }

  @Test
  void boundsMessageWithoutBreakingEscapedHtmlBlocks() {
    var rows =
        IntStream.range(0, 100)
            .mapToObj(
                index ->
                    new PaymentAuditRow(
                        "Property & " + index,
                        "Long tenant name <tenant>",
                        new BigDecimal("3780"),
                        new BigDecimal("3610"),
                        CurrencyType.PLN))
            .toList();
    String html = LongTermPaymentAuditJobHandler.render(rows);
    assertThat(html).hasSizeLessThan(4096).endsWith("\n\n…");
    assertThat(html.split("<b>").length).isEqualTo(html.split("</b>").length);
  }

  @Test
  void rendersEmptyState() {
    assertThat(LongTermPaymentAuditJobHandler.render(List.of()))
        .isEqualTo("<b>Rental income · monthly</b>\nNo active tenant payments found.");
  }
}
