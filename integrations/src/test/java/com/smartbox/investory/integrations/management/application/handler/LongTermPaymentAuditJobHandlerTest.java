package com.smartbox.investory.integrations.management.application.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.longterm.api.LongTermAssetPaymentAuditReader.PaymentAuditRow;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongTermPaymentAuditJobHandlerTest {
  @Test
  void rendersEscapedAlignedColumnsWithBoundedWideAndCombiningUnicode() {
    String html =
        LongTermPaymentAuditJobHandler.render(
            List.of(
                new PaymentAuditRow("A&B/一", "Żółć <x>", new BigDecimal("3780"), CurrencyType.PLN),
                new PaymentAuditRow(
                    "Long property name that is clipped",
                    "Tenant e\u0301",
                    new BigDecimal("3350"),
                    CurrencyType.PLN)));

    assertTrue(html.startsWith("<b>Rental payments · review</b>\n<pre>"));
    assertTrue(html.contains("A&amp;B/一"));
    assertTrue(html.contains("Żółć &lt;x&gt;"));
    assertTrue(html.contains("3,780.00 PLN"));
    assertTrue(html.contains("…"));
    String table = html.substring(html.indexOf("<pre>") + 5, html.indexOf("</pre>"));
    table =
        table
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"");
    String[] lines = table.split("\\n");
    assertTrue(
        cellWidth(lines[0].substring(0, lines[0].indexOf("Tenant")))
            == cellWidth(lines[1].substring(0, lines[1].indexOf("Ż"))));
    assertTrue(cellWidth(lines[0]) == cellWidth(lines[1]));
  }

  @Test
  void rendersEmptyState() {
    assertTrue(
        LongTermPaymentAuditJobHandler.render(List.of())
            .contains("No active tenant payments found."));
  }

  private static int cellWidth(String value) {
    return value
        .codePoints()
        .map(
            cp ->
                cp >= 0x1100
                        && (cp <= 0x115F
                            || cp >= 0x2E80 && cp <= 0xA4CF
                            || cp >= 0x1F300 && cp <= 0x1FAFF)
                    ? 2
                    : 1)
        .sum();
  }
}
