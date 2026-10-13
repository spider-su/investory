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
import java.util.ArrayList;
import java.util.List;
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
      List<String> performanceBoardRequests = new ArrayList<>();
      page.onRequest(
          request -> {
            if (request.url().contains("/investment/performance/board")) {
              performanceBoardRequests.add(request.url());
            }
          });
      long navigationStarted = System.nanoTime();
      var response =
          page.navigate(baseUrl() + "/portfolios/" + PORTFOLIO_ID + "/dashboard?period=MAX");
      long navigationMillis = Duration.ofNanos(System.nanoTime() - navigationStarted).toMillis();

      assertThat(response).isNotNull();
      assertThat(response.status()).isEqualTo(200);
      assertThat(page.title()).contains("Investory");
      assertThat(page.locator("#dashboard-page-data").textContent())
          .contains("\"portfolioId\": 2", "\"selectedDashboardPeriod\": \"MAX\"");
      assertThat(page.locator("#investment-overview").isVisible()).isTrue();
      assertThat(performanceBoardRequests)
          .as("initial chart data is rendered from the dashboard payload")
          .isEmpty();
      assertThat(page.locator(".iv-portfolio-structure").textContent())
          .contains("Cash", "Largest holding", "Asset allocation", "Account currencies");
      assertThat(page.locator("body").textContent())
          .contains("Performance", "Portfolio structure")
          .doesNotContain("Whitelabel Error Page", "Internal Server Error", "Exception:");

      long interactionStarted = System.nanoTime();
      var boardResponse =
          page.waitForResponse(
              candidate -> candidate.url().contains("/investment/performance/board"),
              () -> page.locator(".js-performance-board-metric[data-metric='pl']").click());
      long interactionMillis = Duration.ofNanos(System.nanoTime() - interactionStarted).toMillis();
      assertThat(boardResponse.status()).isEqualTo(200);
      assertThat(performanceBoardRequests).hasSize(1);
      performanceBoardRequests.clear();
      long warmStarted = System.nanoTime();
      var warmResponse = page.reload();
      long warmNavigationMillis = Duration.ofNanos(System.nanoTime() - warmStarted).toMillis();
      assertThat(warmResponse).isNotNull();
      assertThat(warmResponse.status()).isEqualTo(200);
      assertThat(performanceBoardRequests)
          .as("warm initial render also uses the embedded chart payload")
          .isEmpty();
      System.out.printf(
          "Dashboard browser measurements: cold navigation=%dms, warm navigation=%dms, initial performance-board requests=0, setting-change requests=1, setting-change response=%dms%n",
          navigationMillis, warmNavigationMillis, interactionMillis);
    }
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }
}
