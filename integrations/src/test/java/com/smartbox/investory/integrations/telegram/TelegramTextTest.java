package com.smartbox.investory.integrations.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import org.junit.jupiter.api.Test;

class TelegramTextTest {
  @Test
  void escapesDynamicValuesForTelegramHtml() {
    assertEquals("A &amp; &lt;B&gt; &quot;C&quot;", TelegramText.escape("A & <B> \"C\""));
  }

  @Test
  void buildsReadableHeadingMetricAndLink() {
    assertEquals("<b>📊 Portfolio</b>", TelegramText.heading("📊", "Portfolio"));
    assertEquals("<b>Cash:</b> 20,699 USD", TelegramText.metric("Cash", "20,699 USD"));
    assertEquals(
        "<a href=\"https://investory.test/dashboard\">Open dashboard</a>",
        TelegramText.link("Open dashboard", "https://investory.test/dashboard"));
  }
}
