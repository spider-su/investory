package com.smartbox.investory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** Fast browser contract for the dashboard's rendered output. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "investory.time.fixed-instant=2025-12-31T12:00:00Z")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, unit = java.util.concurrent.TimeUnit.MINUTES)
@DisplayName("Investment Dashboard Golden UI")
class InvestmentDashboardGoldenUiIT extends FastDatabaseTest {

  private static final long PORTFOLIO_ID = HappyInvestorTestData.PORTFOLIO_ID;

  @Value("${local.server.port}")
  private int port;

  private Playwright playwright;
  private Browser browser;

  @BeforeAll
  void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  void closeBrowser() {
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
  }

  @Test
  @DisplayName("renders canonical dashboard facts")
  void rendersCanonicalDashboardFacts() {
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setHttpCredentials("admin", "change-me-admin")
                .setViewportSize(1440, 1000))) {
      context.setDefaultTimeout(Duration.ofSeconds(20).toMillis());
      context.setDefaultNavigationTimeout(Duration.ofSeconds(20).toMillis());
      Page page = context.newPage();
      var response =
          page.navigate(baseUrl() + "/portfolios/" + PORTFOLIO_ID + "/dashboard?period=MAX");

      assertThat(response).isNotNull();
      assertThat(response.status()).isEqualTo(200);
      assertThat(page.title()).contains("Investory");
      assertThat(page.locator("#dashboard-page-data").textContent())
          .contains("\"portfolioId\": 2", "\"selectedDashboardPeriod\": \"MAX\"");
      assertThat(page.locator("#investment-overview").isVisible()).isTrue();
      assertThat(page.locator(".iv-portfolio-structure").textContent())
          .contains("Cash", "Largest holding", "Asset allocation", "Account currencies");
      assertThat(page.locator("body").textContent())
          .contains("Performance", "Portfolio structure")
          .doesNotContain("Whitelabel Error Page", "Internal Server Error", "Exception:");
    }
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }
}
