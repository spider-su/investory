package com.smartbox.investory.integrations.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Command Service")
class PortfolioCommandServiceTest {

  private static final String CONTEXT =
      """
            Investory Portfolio performance dashboard Balance 157,972 $ Cash 20,699 $ ROI 5.4% Profit 8160 $ After tax 5983 $ Unrealized P/L -6008 $ Current positions NVIDIA 10% CSPX 8% Realized P/L 12632 $ Dividends 4912 $ Net dividends Unrealized by Currency EUR 0 EUR USD -7326 USD PLN 4937 PLN Realized by Currency EUR 196 EUR USD 11339 USD PLN 3998 PLN
            """;

  @DisplayName("extracts Known Dashboard Metrics")
  @Test
  void extractsKnownDashboardMetrics() {
    assertEquals("157,972 $", PortfolioCommandService.findValue(CONTEXT, "Balance"));
    assertEquals("20,699 $", PortfolioCommandService.findValue(CONTEXT, "Cash"));
    assertEquals("5.4%", PortfolioCommandService.findValue(CONTEXT, "ROI"));
  }

  @DisplayName("extracts Position Section")
  @Test
  void extractsPositionSection() {
    String section =
        PortfolioCommandService.sectionValue(
            CONTEXT, "Current positions", List.of("Realized P/L"), 1000);

    assertTrue(section.contains("NVIDIA 10%"));
    assertTrue(section.contains("CSPX 8%"));
  }
}
