package com.smartbox.investory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorDashboardFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorLongTermFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorMarketDataFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorProfileFacts;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** Read-only browser contract for every financial page backed by the canonical snapshot. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "investory.time.fixed-instant=2025-12-31T12:00:00Z")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("HappyInvestor read-only UI")
class HappyInvestorReadOnlyUiIT extends FastDatabaseTest {
  private static final Path ARTIFACT_DIRECTORY =
      Path.of("target", "ui-test-results", "happyinvestor-read-only");

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
  @DisplayName("dashboard MAX and YTD show the canonical investment snapshot")
  void dashboardMaxAndYtd() throws IOException {
    for (String period : new String[] {"MAX", "YTD"}) {
      assertPage(
          "dashboard-" + period.toLowerCase(),
          "/portfolios/1/dashboard?period=" + period,
          page -> {
            assertThat(page.locator(".iv-period-nav a[aria-current='page']").textContent())
                .isEqualTo("MAX".equals(period) ? "Max" : period);
            assertThat(page.locator("#dashboard-page-data").textContent())
                .contains("\"portfolioId\": 1", "\"selectedDashboardPeriod\": \"" + period + "\"");
            assertThat(page.locator("#balance-cash").textContent())
                .contains(FinancialPresentation.wholeNumber(HappyInvestorDashboardFacts.BALANCE));
            String structure = page.locator(".iv-portfolio-structure").textContent();
            assertThat(structure)
                .contains(
                    "Cash",
                    "Largest holding",
                    "AAPL.US",
                    FinancialPresentation.wholeNumber(HappyInvestorDashboardFacts.APPLE_VALUE),
                    "Top 5",
                    "100.0%",
                    "Equity",
                    "USD",
                    "PLN");
            String positions = page.locator("body").textContent();
            assertThat(positions)
                .contains(
                    "AAPL.US",
                    "TSLA.US",
                    FinancialPresentation.wholeNumber(
                        HappyInvestorDashboardFacts.OPEN_POSITIONS_VALUE),
                    FinancialPresentation.wholeNumber(
                        HappyInvestorDashboardFacts.OPEN_POSITIONS_UNREALIZED));
          });
    }
  }

  @Test
  @DisplayName("profile shows complete whole-wealth values")
  void profile() throws IOException {
    assertPage(
        "profile",
        "/portfolios/1/investment-profile",
        page -> {
          assertThat(page.locator(".iv-planning-topbar").textContent())
              .contains(
                  "Net worth",
                  compact(HappyInvestorProfileFacts.TOTAL_NET_WORTH),
                  "Net income / year",
                  compact(HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL));
          assertThat(page.locator(".iv-profile-source-card").nth(0).textContent())
              .contains("Market investments", "Value", "0", "Annual income (net)", "Received YTD");
          assertThat(page.locator(".iv-profile-source-card").nth(1).textContent())
              .contains(
                  "Long-term assets",
                  compact(HappyInvestorProfileFacts.LONG_TERM_ASSET_VALUE),
                  compact(HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL),
                  FinancialPresentation.percentage(
                      HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL.divide(
                          HappyInvestorLongTermFacts.LONG_TERM_TOTAL,
                          8,
                          java.math.RoundingMode.HALF_UP)));
          assertThat(page.locator(".iv-profile-allocation").textContent())
              .contains(
                  "Short-term assets",
                  "Long-term assets",
                  compact(HappyInvestorProfileFacts.EQUITY_ALLOCATION),
                  compact(HappyInvestorProfileFacts.REAL_ESTATE_ALLOCATION),
                  compact(HappyInvestorProfileFacts.CASH_ALLOCATION),
                  compact(HappyInvestorProfileFacts.OTHER_ALLOCATION),
                  compact(HappyInvestorProfileFacts.FIXED_INCOME_ALLOCATION));
        });
  }

  @Test
  @DisplayName("long-term list and rental detail show canonical economics")
  void longTermListAndDetail() throws IOException {
    assertPage(
        "long-term-list",
        "/portfolios/1/long-term-assets",
        page -> {
          assertThat(page.locator(".iv-planning-topbar").textContent())
              .contains(
                  compact(HappyInvestorLongTermFacts.LONG_TERM_TOTAL),
                  FinancialPresentation.wholeNumber(
                      HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL));
          assertThat(page.locator("#real-estate").textContent())
              .contains("Apartment A", "Apartment B", "3.2K", "3.0K", "8.8%", "6.6%");
          assertThat(page.locator("#bonds").textContent())
              .contains("Treasury 2026", "10.0K", "375", "88");
          assertThat(page.locator("#cash-reserves").textContent())
              .contains("Cash reserve", "50.0K");
          assertThat(page.locator("#other-assets").textContent())
              .contains("Family Car", "Reserve deposit", "50.0K", "1.6K", "380");
        });

    assertPage(
        "long-term-detail",
        "/portfolios/1/long-term-assets/" + HappyInvestorLongTermFacts.APARTMENT_A_ID,
        page -> {
          assertThat(page.locator(".iv-property-hero").textContent())
              .contains("Apartment A", "400,000 PLN");
          assertThat(page.locator("body").textContent())
              .contains(
                  FinancialPresentation.money(
                          HappyInvestorLongTermFacts.APARTMENT_A_MONTHLY_TAX_BASE)
                      + " PLN",
                  "8.8% (9.6%)",
                  "Monthly",
                  "3,200");
        });
  }

  @Test
  @DisplayName("retirement plan and Conservative projection show persisted assumptions")
  void retirementPlanAndProjection() throws IOException {
    assertPage(
        "retirement-plan",
        "/portfolios/1/simulation/plan/edit?planId="
            + HappyInvestorPlanFacts.SEED_PLAN_ID
            + "&planningDisplayCurrency=USD",
        page -> {
          assertThat(page.locator("#plan-name").inputValue())
              .isEqualTo(HappyInvestorPlanFacts.NAME);
          assertInput(page, "#age-at-plan-start", HappyInvestorPlanFacts.CURRENT_AGE);
          assertInput(page, "#retirement-age", HappyInvestorPlanFacts.RETIREMENT_AGE);
          assertInput(page, "#end-age", HappyInvestorPlanFacts.END_AGE);
          assertInput(
              page,
              "#monthly-living-costs",
              HappyInvestorPlanFacts.ANNUAL_LIVING_EXPENSES.divide(BigDecimal.valueOf(12)));
          assertInput(
              page,
              "#discretionary-expenses",
              HappyInvestorPlanFacts.ANNUAL_DISCRETIONARY_EXPENSES);
          assertInput(page, "#employment-income", HappyInvestorPlanFacts.ANNUAL_EMPLOYMENT_INCOME);
          assertInput(
              page,
              "#pre-retirement-contribution",
              HappyInvestorPlanFacts.ANNUAL_PRE_RETIREMENT_CONTRIBUTION);
          assertInput(page, "#annual-pension", HappyInvestorPlanFacts.ANNUAL_PENSION);
        });

    assertPage(
        "retirement-conservative-projection",
        "/portfolios/1/simulation?planId="
            + HappyInvestorPlanFacts.SEED_PLAN_ID
            + "&planningDisplayCurrency=USD&selectedScenario=CONSERVATIVE",
        page -> {
          assertThat(
                  page.locator("#simulation-assumptions-form input[name='selectedScenario']")
                      .inputValue())
              .isEqualTo("CONSERVATIVE");
          assertThat(page.locator(".iv-planning-topbar").textContent())
              .contains(
                  "Conservative",
                  "Planning horizon",
                  HappyInvestorPlanFacts.FIRST_PROJECTED_AGE
                      + "–"
                      + HappyInvestorPlanFacts.END_AGE);
          assertThat(page.locator(".iv-simulation-projection-row").count()).isEqualTo(46);
          assertThat(page.locator("#projection-title").textContent()).contains("Yearly projection");
          assertThat(page.locator("body").textContent())
              .contains(
                  "Spending",
                  "Income",
                  "Gap / surplus",
                  "Cash",
                  "Bonds",
                  "Equities",
                  "Real estate");
        });
  }

  @Test
  @DisplayName("investment asset detail shows all persisted financial sections")
  void investmentAssetDetail() throws IOException {
    assertPage(
        "investment-asset-detail",
        "/portfolios/1/dashboard/assets/TSLA.US?period=MAX",
        page -> {
          assertThat(page.locator("h1").textContent()).isEqualTo("TSLA.US");
          assertThat(new BigDecimal(page.locator("#manual-market-price").inputValue()))
              .isEqualByComparingTo(HappyInvestorMarketDataFacts.TESLA_CLOSE);
          assertThat(page.locator("body").textContent())
              .contains(
                  "403,84 USD",
                  "1,0000",
                  "203,84 USD",
                  "Current holdings",
                  "Closed transactions",
                  "Dividend history",
                  "Reporting snapshot");
        });
  }

  private void assertPage(String name, String path, Consumer<Page> assertions) throws IOException {
    var failures = new ArrayList<String>();
    try (BrowserContext context = authenticatedContext()) {
      context
          .tracing()
          .start(
              new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
      Page page = context.newPage();
      page.onPageError(error -> failures.add("page error: " + error));
      page.onConsoleMessage(
          message -> {
            if ("error".equals(message.type())) failures.add("console error: " + message.text());
          });
      page.onRequestFailed(
          request -> {
            if (request.url().startsWith(baseUrl()))
              failures.add("request failed: " + request.method() + " " + request.url());
          });
      try {
        var response = page.navigate(baseUrl() + path);
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(200);
        assertions.accept(page);
        assertThat(failures).isEmpty();
        context.tracing().stop();
      } catch (AssertionError | RuntimeException failure) {
        saveFailureArtifacts(name, page, context);
        throw failure;
      }
    }
  }

  private static void assertInput(Page page, String selector, int expected) {
    assertThat(page.locator(selector).inputValue()).isEqualTo(Integer.toString(expected));
  }

  private static void assertInput(Page page, String selector, BigDecimal expected) {
    assertThat(new BigDecimal(page.locator(selector).inputValue())).isEqualByComparingTo(expected);
  }

  private static String compact(BigDecimal value) {
    return FinancialPresentation.compactMoney(value);
  }

  private void saveFailureArtifacts(String name, Page page, BrowserContext context)
      throws IOException {
    Files.createDirectories(ARTIFACT_DIRECTORY);
    page.screenshot(
        new Page.ScreenshotOptions()
            .setPath(ARTIFACT_DIRECTORY.resolve(name + ".png"))
            .setFullPage(true));
    Files.writeString(ARTIFACT_DIRECTORY.resolve(name + ".html"), page.content());
    context
        .tracing()
        .stop(new Tracing.StopOptions().setPath(ARTIFACT_DIRECTORY.resolve(name + "-trace.zip")));
  }

  private BrowserContext authenticatedContext() {
    BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setHttpCredentials("admin", "change-me-admin")
                .setViewportSize(1440, 1000));
    context.setDefaultTimeout(Duration.ofMinutes(2).toMillis());
    context.setDefaultNavigationTimeout(Duration.ofMinutes(2).toMillis());
    return context;
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }
}
