package com.smartbox.investory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Tracing;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(
    scripts = "/ui/ui-page-smoke-fixture.sql",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DisplayName("UI Page Smoke")
class UiPageSmokeIT extends FastDatabaseTest {

  private static final Path ARTIFACT_DIRECTORY = Path.of("target", "ui-test-results");

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

  @DisplayName("page Renders Without Browser Errors")
  @ParameterizedTest(name = "{0}")
  @MethodSource("pageCases")
  void pageRendersWithoutBrowserErrors(PageCase pageCase) throws IOException {
    String baseUrl = "http://127.0.0.1:" + port;
    var failures = new ArrayList<String>();
    BrowserContext context = authenticatedContext();
    context
        .tracing()
        .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
    Page page = context.newPage();
    page.onPageError(error -> failures.add("page error: " + error));
    page.onConsoleMessage(
        message -> {
          if ("error".equals(message.type()) && pageCase.expectedStatus() < 400)
            failures.add("console error: " + message.text());
        });
    page.onRequestFailed(
        request -> {
          if (request.url().startsWith(baseUrl))
            failures.add("request failed: " + request.method() + " " + request.url());
        });

    boolean passed = false;
    try {
      Response response = page.navigate(baseUrl + pageCase.path());
      assertThat(response).as("navigation response").isNotNull();
      assertThat(response.status())
          .as("HTTP status for %s (%s)", pageCase.name(), pageCase.path())
          .isEqualTo(pageCase.expectedStatus());
      assertThat(page.title()).contains(pageCase.titleText());
      assertThat(page.locator("body").isVisible()).isTrue();
      if (page.locator("main").count() > 0) assertThat(page.locator("main").isVisible()).isTrue();
      assertThat(page.locator("body").textContent())
          .contains(pageCase.headingText())
          .doesNotContain("Whitelabel Error Page", "Internal Server Error", "Exception:");
      assertThat(failures).isEmpty();
      passed = true;
    } catch (AssertionError | RuntimeException failure) {
      saveFailureArtifacts(pageCase, page, context);
      throw failure;
    } finally {
      if (passed) context.tracing().stop();
      context.close();
    }
  }

  @DisplayName("dashboard Period Navigation Loads Selected Period")
  @Test
  void dashboardPeriodNavigationLoadsSelectedPeriod() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/dashboard?portfolioId=1");

      var yearToDate =
          page.locator(".iv-period-nav a").filter(new Locator.FilterOptions().setHasText("YTD"));
      yearToDate.click();

      assertThat(page.url()).contains("period=YTD");
      assertThat(yearToDate.getAttribute("aria-current")).isEqualTo("page");
    }
  }

  @DisplayName("asset Detail Period Navigation Loads Selected Period")
  @Test
  void assetDetailPeriodNavigationLoadsSelectedPeriod() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/dashboard/assets/AAPL.US");

      page.getByRole(
              com.microsoft.playwright.options.AriaRole.LINK,
              new Page.GetByRoleOptions().setName("YTD"))
          .click();

      assertThat(page.url()).contains("period=YTD");
      assertThat(
              page.getByRole(
                      com.microsoft.playwright.options.AriaRole.LINK,
                      new Page.GetByRoleOptions().setName("YTD"))
                  .getAttribute("aria-current"))
          .isEqualTo("page");
    }
  }

  @DisplayName("long Term Asset Category Can Be Expanded")
  @Test
  void longTermAssetCategoryCanBeExpanded() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/long-term-assets?portfolioId=1");

      var details = page.locator("#real-estate .iv-planning-section__details");
      page.locator("#real-estate .iv-planning-section__header").click();

      assertThat(details.getAttribute("open")).isNotNull();
      assertThat(details.locator("tbody").isVisible()).isTrue();
    }
  }

  @DisplayName("long Term Summary Shows Other Allocation And Cash Economics")
  @Test
  void longTermSummaryShowsOtherAllocationAndCashEconomics() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/long-term-assets?portfolioId=1");

      var other = page.locator("#other-assets");
      assertThat(other.getByText("Other", new Locator.GetByTextOptions().setExact(true)).count())
          .isPositive();
      assertThat(
              other
                  .getByText("UI Other Asset", new Locator.GetByTextOptions().setExact(true))
                  .count())
          .isPositive();
      assertThat(page.locator(".iv-long-term-allocation__legend").textContent()).contains("Other");

      page.locator("#cash-reserves .iv-planning-section__header").click();
      assertThat(page.locator("#cash-reserves").textContent()).contains("1.0%", "150");
    }
  }

  @DisplayName("investment Profile Shows Module Owned Summary Semantics")
  @Test
  void investmentProfileShowsModuleOwnedSummarySemantics() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/investment-profile?portfolioId=1");

      String header = page.locator(".iv-planning-topbar").textContent();
      assertThat(header)
          .contains(
              "Net worth",
              "Net income / year",
              "Annual cost / year",
              "planned · " + Year.now().getValue())
          .doesNotContain("Market return");

      Locator sourceCards = page.locator(".iv-profile-source-card");
      assertThat(sourceCards.count()).isEqualTo(2);
      String marketCard = sourceCards.nth(0).textContent();
      String longTermCard = sourceCards.nth(1).textContent();
      assertThat(marketCard).contains("Received YTD", "Annualized return").doesNotContain("p.a.");
      assertThat(
              sourceCards
                  .nth(0)
                  .locator(".iv-profile-source-card__metrics > div")
                  .nth(2)
                  .locator("strong")
                  .textContent())
          .matches("-?\\d+\\.\\d%|Unavailable");
      assertThat(longTermCard).contains("Received YTD").doesNotContain("Basis");

      String allocation = page.locator(".iv-profile-allocation").textContent();
      assertThat(allocation)
          .contains("Short-term assets", "Long-term assets", "Asset type")
          .containsAnyOf("Short-term asset", "Long-term asset")
          .containsAnyOf("Cash · short-term", "Other · long-term")
          .doesNotContain("Liquid", "Illiquid");

      String sourceLongTermPercentage = percentageIn(longTermCard);
      String allocationLongTermPercentage = percentageAfter(allocation, "Long-term assets ");
      assertThat(sourceLongTermPercentage).isEqualTo(allocationLongTermPercentage);
    }
  }

  private static String percentageIn(String text) {
    var matcher = Pattern.compile("(-?\\d+\\.\\d%)").matcher(text);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private static String percentageAfter(String text, String prefix) {
    var matcher = Pattern.compile(Pattern.quote(prefix) + "(-?\\d+\\.\\d%)").matcher(text);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  @DisplayName("real Estate Contract Edit Can Be Opened And Cancelled")
  @Test
  void realEstateContractEditCanBeOpenedAndCancelled() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/long-term-assets/9105?portfolioId=1");

      page.locator("[data-edit-contract]").click();
      assertThat(page.locator("[data-contract-edit]").isVisible()).isTrue();

      page.locator("[data-cancel-edit]").click();
      assertThat(page.locator("[data-contract-read]").isVisible()).isTrue();
    }
  }

  @DisplayName("simulation Year Control Changes Visible Snapshot")
  @Test
  void simulationYearControlChangesVisibleSnapshot() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/simulation?portfolioId=1&planId=9201");
      String initialYear = page.locator("#plan-year-selector").inputValue();

      page.locator("[data-plan-next]").click();

      assertThat(page.locator("#plan-year-selector").inputValue()).isNotEqualTo(initialYear);
      assertThat(page.locator("[data-plan-snapshot]:visible").count()).isEqualTo(1);
    }
  }

  @DisplayName("plan Editor Shows Invalid Retirement Age")
  @Test
  void planEditorShowsInvalidRetirementAge() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/simulation/plan/edit?portfolioId=1&planId=9201");

      page.locator("#retirement-age").fill("90");

      assertThat(page.locator("[data-plan-error='retirementAge']").textContent())
          .contains("cannot be after plan exit age");
    }
  }

  @DisplayName("retirement Analysis Tabs Switch Panels")
  @Test
  void retirementAnalysisTabsSwitchPanels() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/analysis?portfolioId=1&planId=9201");

      page.locator("[data-analysis-tab='risk']").click();

      assertThat(page.locator("#analysis-panel-risk").isVisible()).isTrue();
      assertThat(page.locator("[data-analysis-tab='risk']").getAttribute("aria-selected"))
          .isEqualTo("true");
    }
  }

  @DisplayName("settings Page Uses Shared Navigation And Provider Forms")
  @Test
  void settingsPageUsesSharedNavigationAndProviderForms() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/settings/integrations");

      assertThat(
              page.locator(".iv-page-nav")
                  .getByText("Settings", new Locator.GetByTextOptions().setExact(true))
                  .count())
          .isPositive();
      assertThat(page.locator(".iv-settings-card").count()).isGreaterThan(0);
      assertThat(page.locator(".iv-settings-card").first().locator("button[type='submit']").count())
          .isPositive();
      assertThat(page.locator("body").textContent()).doesNotContain("apiKey");
    }
  }

  @DisplayName("long Term Asset Dates Use Readable Display Format")
  @Test
  void longTermAssetDatesUseReadableDisplayFormat() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/long-term-assets/9105?portfolioId=1");

      assertThat(page.locator(".iv-rental-contract__date").first().textContent())
          .containsPattern("\\b\\d{1,2} [A-Z][a-z]{2} \\d{4}\\b")
          .doesNotContain("2026-");
    }
  }

  Stream<Arguments> pageCases() {
    int currentYear = Year.now().getValue();
    return Stream.of(
            new PageCase("home", "/", 200, "Investory", "Welcome to Investory"),
            new PageCase(
                "dashboard", "/dashboard?portfolioId=1", 200, "Investory", "Portfolio structure"),
            new PageCase("asset detail", "/dashboard/assets/AAPL.US", 200, "AAPL.US", "AAPL.US"),
            new PageCase(
                "asset not found",
                "/dashboard/assets/UI-NOT-FOUND",
                404,
                "Asset not found",
                "Asset not found"),
            new PageCase(
                "reconciliation",
                "/dashboard/reconciliation",
                200,
                "Reconciliation",
                "Reconciliation"),
            new PageCase(
                "long-term assets",
                "/long-term-assets?portfolioId=1",
                200,
                "Long-term assets",
                "Long-term assets"),
            new PageCase(
                "new other asset",
                "/long-term-assets/new?portfolioId=1",
                200,
                "Long-term asset",
                "Long-term asset"),
            new PageCase(
                "new bond",
                "/long-term-assets/new/bond?portfolioId=1",
                200,
                "New bond",
                "New bond"),
            new PageCase(
                "new cash reserve",
                "/long-term-assets/new/cash-reserve?portfolioId=1",
                200,
                "Cash reserve",
                "New cash reserve"),
            new PageCase(
                "new deposit",
                "/long-term-assets/new/deposit?portfolioId=1",
                200,
                "New deposit",
                "New deposit"),
            new PageCase(
                "new real estate",
                "/long-term-assets/new/real-estate?portfolioId=1",
                200,
                "Real estate",
                "Rental property"),
            new PageCase(
                "other asset detail",
                "/long-term-assets/9101?portfolioId=1",
                200,
                "UI Other Asset",
                "UI Other Asset"),
            new PageCase(
                "bond detail", "/long-term-assets/9102?portfolioId=1", 200, "UI Bond", "UI Bond"),
            new PageCase(
                "cash reserve detail",
                "/long-term-assets/9103?portfolioId=1",
                200,
                "UI Cash Reserve",
                "UI Cash Reserve"),
            new PageCase(
                "deposit detail",
                "/long-term-assets/9104?portfolioId=1",
                200,
                "UI Deposit",
                "UI Deposit"),
            new PageCase(
                "real estate detail",
                "/long-term-assets/9105?portfolioId=1",
                200,
                "UI Rental Home",
                "UI Rental Home"),
            new PageCase(
                "investment profile",
                "/investment-profile?portfolioId=1",
                200,
                "Profile",
                "Income sources"),
            new PageCase(
                "simulation",
                "/simulation?portfolioId=1&planId=9201",
                200,
                "Retirement simulation",
                "Plan timeline"),
            new PageCase(
                "plan editor",
                "/simulation/plan/edit?portfolioId=1&planId=9201",
                200,
                "Edit plan",
                "Edit plan"),
            new PageCase(
                "retirement analysis",
                "/analysis?portfolioId=1&planId=9201",
                200,
                "Retirement analysis",
                "Economic risks"),
            new PageCase(
                "live year review",
                "/simulation/timeline/" + currentYear + "?portfolioId=1&planId=9201",
                200,
                "Live year review",
                "Live Year Review"),
            new PageCase(
                "past year review",
                "/simulation/timeline/2025?portfolioId=1&planId=9201",
                200,
                "Year review",
                "2025 Year review"),
            new PageCase(
                "integration settings",
                "/settings/integrations",
                200,
                "Integration settings",
                "Integration settings"))
        .map(page -> Arguments.of(Named.of(page.name(), page)));
  }

  private void saveFailureArtifacts(PageCase pageCase, Page page, BrowserContext context)
      throws IOException {
    Files.createDirectories(ARTIFACT_DIRECTORY);
    String name = pageCase.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
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

  private record PageCase(
      String name, String path, int expectedStatus, String titleText, String headingText) {}
}
