package com.smartbox.investory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorDashboardFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorLongTermFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorMarketDataFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorProfileFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

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

  @Autowired private PortfolioProjectionService projections;

  @Autowired private PortfolioProjectionRefreshService projectionRefresh;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeAll
  void prepareProjectionAndLaunchBrowser() {
    jdbc.update(
        "INSERT INTO investory.app_users "
            + "(username, display_name, active, password_hash, role) "
            + "VALUES (?, ?, true, ?, 'USER') "
            + "ON CONFLICT (username) DO UPDATE SET active = true, password_hash = EXCLUDED.password_hash, role = 'USER'",
        "happy.profile.user",
        "Happy Profile User",
        passwordEncoder.encode("happy-profile-password"));
    jdbc.update(
        "INSERT INTO investory.profile_memberships (user_id, profile_id, role) "
            + "SELECT id, "
            + HappyInvestorTestData.PORTFOLIO_ID
            + ", 'USER' FROM investory.app_users WHERE username = ? "
            + "ON CONFLICT (user_id, profile_id) DO UPDATE SET role = 'USER'",
        "happy.profile.user");
    prepareCanonicalBoundary();
    projections.recalculateAccounts(
        Set.of(
            HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
            HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
            HappyInvestorTestData.XTB_PLN_ACCOUNT_ID,
            HappyInvestorTestData.XTB_EUR_ACCOUNT_ID));
    projectionRefresh.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.DASHBOARD);
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  private void prepareCanonicalBoundary() {
    jdbc.update(
        "DELETE FROM investory.positions WHERE open_time::date > ?",
        HappyInvestorTestData.REFERENCE_DATE);
    jdbc.update(
        "UPDATE investory.positions SET close_time = NULL WHERE close_time::date > ?",
        HappyInvestorTestData.REFERENCE_DATE);
    jdbc.update(
        "DELETE FROM investory.cash_operations WHERE date::date > ?",
        HappyInvestorTestData.REFERENCE_DATE);
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
    jdbc.update(
        """
        INSERT INTO investory.exchange_rates(
            rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference)
        WITH anchors AS (
          SELECT
            (SELECT rate FROM investory.exchange_rates WHERE rate_date = ? AND base = 'USD' AND to_currency = 'PLN' ORDER BY id DESC LIMIT 1) AS usd_pln,
            (SELECT rate FROM investory.exchange_rates WHERE rate_date = ? AND base = 'EUR' AND to_currency = 'USD' ORDER BY id DESC LIMIT 1) AS eur_usd
        ), matrix(source_currency, target_currency, rate) AS (
          SELECT 'USD', 'PLN', usd_pln FROM anchors
          UNION ALL SELECT 'PLN', 'USD', 1 / usd_pln FROM anchors
          UNION ALL SELECT 'EUR', 'USD', eur_usd FROM anchors
          UNION ALL SELECT 'USD', 'EUR', 1 / eur_usd FROM anchors
          UNION ALL SELECT 'EUR', 'PLN', eur_usd * usd_pln FROM anchors
          UNION ALL SELECT 'PLN', 'EUR', 1 / (eur_usd * usd_pln) FROM anchors
        )
        SELECT CURRENT_DATE, source_currency, target_currency, rate,
               'TEST', 'OBSERVED', ?, 'HAPPYINVESTOR_REFERENCE'
        FROM matrix
        ON CONFLICT (rate_date, base, to_currency) WHERE purpose = 'VALUATION' DO UPDATE
          SET rate = EXCLUDED.rate,
              source = EXCLUDED.source,
              method = EXCLUDED.method,
              source_rate_date = EXCLUDED.source_rate_date,
              source_reference = EXCLUDED.source_reference
        """,
        HappyInvestorTestData.REFERENCE_DATE,
        HappyInvestorTestData.REFERENCE_DATE,
        HappyInvestorTestData.REFERENCE_DATE);
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
          "/portfolios/" + HappyInvestorTestData.PORTFOLIO_ID + "/dashboard?period=" + period,
          page -> {
            assertThat(page.locator(".iv-period-nav a[aria-current='page']").textContent())
                .isEqualTo("MAX".equals(period) ? "Max" : period);
            assertThat(page.locator("#dashboard-page-data").textContent())
                .contains("\"portfolioId\": 2", "\"selectedDashboardPeriod\": \"" + period + "\"");
            assertThat(page.locator("#balance-cash").textContent())
                .contains(FinancialPresentation.compactMoney(HappyInvestorDashboardFacts.BALANCE));
            String structure = page.locator(".iv-portfolio-structure").textContent();
            assertThat(structure)
                .contains(
                    "Cash",
                    "Largest holding",
                    "AAPL.US",
                    FinancialPresentation.compactMoney(HappyInvestorDashboardFacts.APPLE_VALUE),
                    "Top 5",
                    "100.0%",
                    "Equity",
                    "USD",
                    "PLN");
            String positions = page.locator("body").textContent();
            assertThat(positions)
                .contains(
                    FinancialPresentation.compactMoney(HappyInvestorDashboardFacts.NET_DEPOSITS),
                    FinancialPresentation.compactMoney(HappyInvestorDashboardFacts.DEPOSITS),
                    FinancialPresentation.compactMoney(HappyInvestorDashboardFacts.WITHDRAWALS),
                    "AAPL.US",
                    "TSLA.US",
                    FinancialPresentation.compactMoney(
                        HappyInvestorDashboardFacts.OPEN_POSITIONS_VALUE),
                    FinancialPresentation.compactMoney(
                        HappyInvestorDashboardFacts.OPEN_POSITIONS_UNREALIZED));
          });
    }
  }

  @Test
  @DisplayName("profile shows complete whole-wealth values")
  void profile() throws IOException {
    assertPage(
        "profile",
        "/portfolios/" + HappyInvestorTestData.PORTFOLIO_ID + "/investment-profile",
        page -> {
          assertThat(page.locator(".iv-planning-topbar").textContent())
              .contains(
                  "Net worth",
                  compact(HappyInvestorProfileFacts.TOTAL_NET_WORTH),
                  "Net income / year",
                  "77.2K");
          assertThat(page.locator(".iv-profile-source-card").nth(0).textContent())
              .contains(
                  "Market investments",
                  "Income base",
                  "Projected annual income (net)",
                  "Investment result YTD");
          assertThat(page.locator(".iv-profile-source-card").nth(1).textContent())
              .contains(
                  "Long-term assets",
                  compact(HappyInvestorProfileFacts.LONG_TERM_ASSET_VALUE),
                  compact(HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL),
                  FinancialPresentation.percentage(
                      HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL.divide(
                          HappyInvestorLongTermFacts.LONG_TERM_TOTAL.subtract(
                              HappyInvestorProfileFacts.OTHER_ALLOCATION),
                          8,
                          java.math.RoundingMode.HALF_UP)));
          assertThat(page.locator(".iv-profile-allocation").textContent())
              .contains(
                  "Short-term assets",
                  "Long-term assets",
                  compact(HappyInvestorProfileFacts.EQUITY_ALLOCATION),
                  compact(HappyInvestorProfileFacts.REAL_ESTATE_ALLOCATION),
                  "25.0K",
                  compact(HappyInvestorProfileFacts.OTHER_ALLOCATION),
                  compact(HappyInvestorProfileFacts.FIXED_INCOME_ALLOCATION));
        });
  }

  @Test
  @DisplayName("long-term list and rental detail show canonical economics")
  void longTermListAndDetail() throws IOException {
    assertPage(
        "long-term-list",
        "/portfolios/" + HappyInvestorTestData.PORTFOLIO_ID + "/long-term-assets",
        page -> {
          assertThat(page.locator(".iv-planning-topbar").textContent())
              .contains(
                  compact(HappyInvestorLongTermFacts.LONG_TERM_TOTAL),
                  FinancialPresentation.wholeNumber(
                      HappyInvestorLongTermFacts.AGGREGATE_NET_ANNUAL));
          assertThat(page.locator("#real-estate").textContent())
              .contains(
                  "Apartment A",
                  "Apartment B",
                  "Rent tax / month",
                  "267",
                  "250",
                  "Rent tax / month",
                  "517",
                  "9.5%",
                  "7.1%");
          assertThat(page.locator("#bonds").textContent())
              .contains("Treasury 2026", "10.0K", "375", "88");
          assertThat(page.locator("#cash-reserves").textContent())
              .contains("Cash reserve", "Term cash reserve", "25.0K", "810", "190");
          assertThat(page.locator("#personal-assets").textContent()).contains("Family Car");
        });

    assertPage(
        "long-term-detail",
        "/portfolios/"
            + HappyInvestorTestData.PORTFOLIO_ID
            + "/long-term-assets/"
            + HappyInvestorLongTermFacts.APARTMENT_A_ID
            + "/real-estate",
        page -> {
          assertThat(page.locator(".iv-property-hero").textContent())
              .contains("Apartment A", "400,000 PLN");
          assertThat(page.locator("body").textContent())
              .contains(
                  "Rental contracts",
                  "Property settings",
                  "Annual tax base",
                  "Tax rate",
                  "Tax / year",
                  "Tax / month",
                  "3,200 PLN",
                  "8.5%",
                  "272 PLN",
                  "22.67 PLN",
                  FinancialPresentation.money(HappyInvestorTestData.APARTMENT_A_MONTHLY_RENT),
                  "Monthly");
          assertThat(page.locator("label[for='tax-base']").textContent())
              .isEqualTo("Annual rental tax base");
          assertThat(page.locator("#tax-base").inputValue()).isEqualTo("3200");
          assertThat(page.locator("#land-register-number").inputValue())
              .isEqualTo("KR1P/4322432/0");
        });

    assertPage(
        "long-term-bond-form",
        "/portfolios/"
            + HappyInvestorTestData.PORTFOLIO_ID
            + "/long-term-assets/"
            + HappyInvestorLongTermFacts.TREASURY_ID
            + "/bond",
        page ->
            assertThat(page.locator("#bond-rate").inputValue())
                .as("bond form uses the documented two-decimal presentation boundary")
                .isEqualTo(HappyInvestorLongTermFacts.TREASURY_FORM_RATE_DISPLAY));
  }

  @Test
  @DisplayName("retirement plan and Conservative projection show persisted assumptions")
  void retirementPlanAndProjection() throws IOException {
    assertPage(
        "retirement-plan",
        "/portfolios/"
            + HappyInvestorTestData.PORTFOLIO_ID
            + "/simulation/plan/edit?planId="
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
        "/portfolios/"
            + HappyInvestorTestData.PORTFOLIO_ID
            + "/simulation?planId="
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
        "/portfolios/"
            + HappyInvestorTestData.PORTFOLIO_ID
            + "/dashboard/assets/TSLA.US?period=MAX",
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

  @Test
  @DisplayName("profile user can read the dashboard but receives read-only capabilities")
  void profileUser_isReadOnly() throws IOException {
    var failures = new ArrayList<String>();
    try (BrowserContext context =
        authenticatedContext("happy.profile.user", "happy-profile-password")) {
      Page page = context.newPage();
      page.onPageError(error -> failures.add("page error: " + error));
      var response =
          page.navigate(
              baseUrl()
                  + "/portfolios/"
                  + HappyInvestorTestData.PORTFOLIO_ID
                  + "/dashboard?period=MAX");
      assertThat(response).isNotNull();
      assertThat(response.status()).isEqualTo(200);
      assertThat(page.evaluate("window.investoryCapabilities.canEdit")).isEqualTo(false);
      assertThat(page.evaluate("window.investoryCapabilities.canImport")).isEqualTo(false);
      assertThat(page.locator("form[method='post'] button:enabled").count()).isZero();
      assertThat(failures).isEmpty();
    }
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
    return authenticatedContext("admin", "change-me-admin");
  }

  private BrowserContext authenticatedContext(String username, String password) {
    BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setHttpCredentials(username, password)
                .setViewportSize(1440, 1000));
    context.setDefaultTimeout(Duration.ofMinutes(2).toMillis());
    context.setDefaultNavigationTimeout(Duration.ofMinutes(2).toMillis());
    return context;
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }
}
