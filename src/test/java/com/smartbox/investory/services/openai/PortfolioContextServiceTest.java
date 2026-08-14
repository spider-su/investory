package com.smartbox.investory.services.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortfolioContextServiceTest {

  @Test
  void detectsPortfolioQuestions() {
    assertTrue(PortfolioContextService.isPortfolioQuestion("What is my portfolio balance?"));
    assertTrue(PortfolioContextService.isPortfolioQuestion("Show unrealized P/L"));
    assertTrue(PortfolioContextService.isPortfolioQuestion("How much cash do I have?"));
    assertFalse(PortfolioContextService.isPortfolioQuestion("What is an ETF?"));
  }

  @Test
  void convertsDashboardHtmlToCompactText() {
    String html =
        """
                <html>
                  <style>.hidden { display:none }</style>
                  <script>alert('ignore')</script>
                  <body>
                    <h1>Portfolio</h1>
                    <div>Balance: <strong>$157,972</strong></div>
                    <div>Cash: 20,699 &amp; ROI: 5.4%</div>
                  </body>
                </html>
                """;

    assertEquals(
        "Portfolio Balance: $157,972 Cash: 20,699 & ROI: 5.4%",
        PortfolioContextService.htmlToText(html));
  }
}
