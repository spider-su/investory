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
import java.time.Clock;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("UI Page Smoke")
class UiPageSmokeIT extends FastDatabaseTest {

  private static final Path ARTIFACT_DIRECTORY = Path.of("target", "ui-test-results");

  @Value("${local.server.port}")
  private int port;

  @Autowired private Clock applicationClock;

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
    var writes = new ArrayList<String>();
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
    page.onRequest(
        request -> {
          if (request.url().startsWith(baseUrl)
              && !Set.of("GET", "HEAD", "OPTIONS").contains(request.method()))
            writes.add(request.method() + " " + request.url());
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
      assertThat(writes).as("read-only route navigation").isEmpty();
      passed = true;
    } catch (AssertionError | RuntimeException failure) {
      saveFailureArtifacts(pageCase, page, context);
      throw failure;
    } finally {
      if (passed) context.tracing().stop();
      context.close();
    }
  }

  @DisplayName("primary pages render at narrow viewport without whole-page overflow")
  @Test
  void primaryPagesRenderAtNarrowViewportWithoutOverflow() throws IOException {
    for (Object argument : pageCases().toList()) {
      PageCase pageCase = (PageCase) ((Arguments) argument).get()[0];
      var failures = new ArrayList<String>();
      var writes = new ArrayList<String>();
      try (BrowserContext context = narrowAuthenticatedContext()) {
        Page page = context.newPage();
        page.onPageError(error -> failures.add("page error: " + error));
        page.onConsoleMessage(
            message -> {
              if ("error".equals(message.type()) && pageCase.expectedStatus() < 400)
                failures.add("console error: " + message.text());
            });
        page.onRequest(
            request -> {
              if (request.url().startsWith(baseUrl())
                  && !Set.of("GET", "HEAD", "OPTIONS").contains(request.method()))
                writes.add(request.method() + " " + request.url());
            });
        Response response = page.navigate(baseUrl() + pageCase.path());
        assertThat(response).as("navigation response for %s", pageCase.name()).isNotNull();
        assertThat(response.status()).isEqualTo(pageCase.expectedStatus());
        assertThat(page.locator("body").isVisible()).isTrue();
        assertThat(page.locator("body").textContent()).contains(pageCase.headingText());
        assertThat(
                page.evaluate(
                    "document.documentElement.scrollWidth <= document.documentElement.clientWidth"))
            .as("no whole-page horizontal overflow for %s", pageCase.name())
            .isEqualTo(true);
        assertThat(failures).isEmpty();
        assertThat(writes).as("read-only narrow route navigation").isEmpty();
      }
    }
  }

  @DisplayName("dashboard Period Navigation Loads Selected Period")
  @Test
  void dashboardPeriodNavigationLoadsSelectedPeriod() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/portfolios/2/dashboard?");

      var yearToDate =
          page.locator(".iv-period-nav a").filter(new Locator.FilterOptions().setHasText("YTD"));
      yearToDate.click();

      assertThat(page.url()).contains("period=YTD");
      assertThat(yearToDate.getAttribute("aria-current")).isEqualTo("page");
    }
  }

  @DisplayName("shared Navigation Preserves The Cross Page Journey Across Back And Forward")
  @Test
  void sharedNavigationPreservesCrossPageJourneyAcrossBackAndForward() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/portfolios/2/dashboard?");

      clickNavigation(page, "Long-term assets");
      assertThat(page.url()).contains("/long-term-assets");
      clickNavigation(page, "Profile");
      assertThat(page.url()).contains("/investment-profile");
      clickNavigation(page, "Simulation");
      assertThat(page.url()).contains("/simulation");

      page.goBack();
      assertThat(page.url()).contains("/investment-profile");
      page.goBack();
      assertThat(page.url()).contains("/long-term-assets");
      page.goForward();
      assertThat(page.url()).contains("/investment-profile");
      page.goForward();
      assertThat(page.url()).contains("/simulation");
    }
  }

  private static void clickNavigation(Page page, String name) {
    page.waitForNavigation(
        () ->
            page.getByRole(
                    com.microsoft.playwright.options.AriaRole.LINK,
                    new Page.GetByRoleOptions().setName(name).setExact(true))
                .click());
  }

  @DisplayName("asset Detail Period Navigation Loads Selected Period")
  @Test
  void assetDetailPeriodNavigationLoadsSelectedPeriod() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/portfolios/2/dashboard/assets/AAPL.US?");

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
      page.navigate(baseUrl() + "/portfolios/2/long-term-assets?");

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
      page.navigate(baseUrl() + "/portfolios/2/long-term-assets?");

      var personal = page.locator("#personal-assets");
      assertThat(
              personal
                  .getByText("Personal assets", new Locator.GetByTextOptions().setExact(true))
                  .count())
          .isPositive();
      assertThat(
              personal
                  .getByText("Family Car", new Locator.GetByTextOptions().setExact(true))
                  .count())
          .isPositive();
      assertThat(page.locator(".iv-long-term-allocation__legend").textContent())
          .contains("Personal assets");

      page.locator("#cash-reserves .iv-planning-section__header").click();
      assertThat(page.locator("#cash-reserves").textContent()).contains("Cash reserve");
    }
  }

  @DisplayName("investment Profile Shows Module Owned Summary Semantics")
  @Test
  void investmentProfileShowsModuleOwnedSummarySemantics() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/portfolios/2/investment-profile?");

      String header = page.locator(".iv-planning-topbar").textContent();
      assertThat(header)
          .contains("Net worth", "Net income / year", "Annual cost / year", "projected · net")
          .doesNotContain("Market return");

      Locator sourceCards = page.locator(".iv-profile-source-card");
      assertThat(sourceCards.count()).isEqualTo(2);
      String marketCard = sourceCards.nth(0).textContent();
      String longTermCard = sourceCards.nth(1).textContent();
      assertThat(marketCard)
          .contains(
              "Investment result YTD",
              "Expected annual investment result",
              "Forward-looking estimate",
              "Historical annualized TWR")
          .doesNotContain("p.a.");
      assertThat(
              sourceCards
                  .nth(0)
                  .locator(".iv-profile-source-card__metrics > div")
                  .nth(2)
                  .locator("strong")
                  .textContent())
          .matches("-?\\d+\\.\\d%|Unavailable");
      assertThat(longTermCard).contains("Planned income YTD").doesNotContain("Basis");

      String allocation = page.locator(".iv-profile-allocation").textContent();
      assertThat(allocation)
          .contains("Short-term assets", "Long-term assets", "Asset type")
          .containsAnyOf("Short-term asset", "Long-term asset")
          .containsAnyOf("Cash · Long-term asset", "Other · Long-term asset")
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
      page.navigate(baseUrl() + "/portfolios/2/long-term-assets/9402/real-estate?");

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
      page.navigate(baseUrl() + "/portfolios/2/simulation?&planId=9201");
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
      page.navigate(baseUrl() + "/portfolios/2/simulation/plan/edit?&planId=9201");

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
      page.navigate(baseUrl() + "/portfolios/2/analysis?&planId=9201");

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

  @DisplayName("integration Settings Configure Control Reveals Provider Form")
  @Test
  void integrationSettingsConfigureControlRevealsProviderForm() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/settings/integrations");

      Locator provider = page.locator(".iv-settings-card").first();
      Locator configure = provider.locator("details.iv-settings-details > summary");
      Locator form = provider.locator("form.iv-settings-form");

      assertThat(configure.isVisible()).isTrue();
      assertThat(form.isVisible()).isFalse();
      configure.click();

      assertThat(provider.locator("details.iv-settings-details").getAttribute("open")).isNotNull();
      assertThat(form.isVisible()).isTrue();
      assertThat(
              form.getByRole(
                      com.microsoft.playwright.options.AriaRole.BUTTON,
                      new Locator.GetByRoleOptions().setName("Save changes").setExact(true))
                  .isEnabled())
          .isTrue();
    }
  }

  @DisplayName("investment Profile Income Summary Popover Opens")
  @Test
  void investmentProfileIncomeSummaryPopoverOpens() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/portfolios/2/investment-profile?");

      Locator incomeSummary = page.locator(".iv-planning-summary__item.iv-hover-context");
      Locator popover = incomeSummary.locator("[role='tooltip']");

      assertThat(incomeSummary.isVisible()).isTrue();
      incomeSummary.hover();

      assertThat(popover.isVisible()).isTrue();
      assertThat(popover.textContent())
          .contains("Annual income", "Market projected", "Long-term expected", "Total");
    }
  }

  @DisplayName("reconciliation Shared Navigation Opens Profile")
  @Test
  void reconciliationSharedNavigationOpensProfile() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/portfolios/2/dashboard/reconciliation?");

      Locator profile =
          page.locator(".iv-page-nav")
              .getByText("Profile", new Locator.GetByTextOptions().setExact(true));
      assertThat(profile.isVisible()).isTrue();
      profile.click();

      assertThat(page.url()).contains("/portfolios/2/investment-profile");
      assertThat(page.locator("#sources-title").textContent()).isEqualTo("Income sources");
    }
  }

  @DisplayName("long Term Asset Dates Use Readable Display Format")
  @Test
  void longTermAssetDatesUseReadableDisplayFormat() {
    try (BrowserContext context = authenticatedContext()) {
      Page page = context.newPage();
      page.navigate(baseUrl() + "/portfolios/2/long-term-assets/9402/real-estate?");

      assertThat(page.locator(".iv-rental-contract__date").first().textContent())
          .containsPattern("\\b\\d{1,2} [A-Z][a-z]{2} \\d{4}\\b")
          .doesNotContain("2026-");
    }
  }

  Stream<Arguments> pageCases() {
    int currentYear = Year.now(applicationClock).getValue();
    return Stream.of(
            new PageCase("home", "/", 200, "Investory", "Welcome to Investory"),
            new PageCase(
                "dashboard", "/portfolios/2/dashboard?", 200, "Investory", "Portfolio structure"),
            new PageCase(
                "asset detail",
                "/portfolios/2/dashboard/assets/AAPL.US?",
                200,
                "AAPL.US",
                "AAPL.US"),
            new PageCase(
                "asset not found",
                "/portfolios/2/dashboard/assets/UI-NOT-FOUND?",
                404,
                "Asset not found",
                "Asset not found"),
            new PageCase(
                "reconciliation",
                "/portfolios/2/dashboard/reconciliation?",
                200,
                "Reconciliation",
                "Reconciliation"),
            new PageCase(
                "long-term assets",
                "/portfolios/2/long-term-assets?",
                200,
                "Long-term assets",
                "Long-term assets"),
            new PageCase(
                "new bond", "/portfolios/2/long-term-assets/new/bond?", 200, "Bond", "Add bond"),
            new PageCase(
                "new cash reserve",
                "/portfolios/2/long-term-assets/new/cash-reserve?",
                200,
                "Cash reserve",
                "Add cash reserve"),
            new PageCase(
                "new real estate",
                "/portfolios/2/long-term-assets/new/real-estate?",
                200,
                "Real estate",
                "Add real estate"),
            new PageCase(
                "new personal asset",
                "/portfolios/2/long-term-assets/new/personal-asset?",
                200,
                "Personal asset",
                "Add personal asset"),
            new PageCase(
                "cash reserve detail",
                "/portfolios/2/long-term-assets/9401/cash-reserve?",
                200,
                "Cash reserve",
                "Edit cash reserve"),
            new PageCase(
                "bond detail",
                "/portfolios/2/long-term-assets/9405/bond?",
                200,
                "Bond",
                "Edit bond"),
            new PageCase(
                "interest-bearing cash reserve detail",
                "/portfolios/2/long-term-assets/9406/cash-reserve?",
                200,
                "Cash reserve",
                "Edit cash reserve"),
            new PageCase(
                "personal asset detail",
                "/portfolios/2/long-term-assets/9404/personal-asset?",
                200,
                "Personal asset",
                "Edit personal asset"),
            new PageCase(
                "apartment A detail",
                "/portfolios/2/long-term-assets/9402/real-estate?",
                200,
                "Apartment A",
                "Apartment A"),
            new PageCase(
                "investment profile",
                "/portfolios/2/investment-profile?",
                200,
                "Profile",
                "Income sources"),
            new PageCase(
                "simulation",
                "/portfolios/2/simulation?&planId=9201",
                200,
                "Retirement simulation",
                "Plan timeline"),
            new PageCase(
                "retirement sandbox",
                "/portfolios/2/simulation/sandbox",
                200,
                "Retirement sandbox",
                "NOK — spending is not fully funded"),
            new PageCase(
                "plan editor",
                "/portfolios/2/simulation/plan/edit?&planId=9201",
                200,
                "Edit plan",
                "Edit plan"),
            new PageCase(
                "retirement analysis",
                "/portfolios/2/analysis?&planId=9201",
                200,
                "Retirement analysis",
                "Economic risks"),
            new PageCase(
                "live year review",
                "/portfolios/2/simulation/timeline/" + currentYear + "?&planId=9201",
                200,
                "Live year review",
                "Live Year Review"),
            new PageCase(
                "past year review",
                "/portfolios/2/simulation/timeline/2025?&planId=9201",
                200,
                "Year review",
                "2025 Year review"),
            new PageCase(
                "integration settings",
                "/settings/integrations",
                200,
                "Integration settings",
                "Integration settings"))
        .map(Arguments::of);
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

  private BrowserContext narrowAuthenticatedContext() {
    BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setHttpCredentials("admin", "change-me-admin")
                .setViewportSize(390, 844));
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
