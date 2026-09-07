package com.smartbox.investory.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.AriaRole;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorLongTermFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "investory.time.fixed-instant=2025-12-31T12:00:00Z")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Long Term Asset Crud UI")
class LongTermAssetCrudUiIT extends FastDatabaseTest {
  private static final Path ARTIFACT_DIRECTORY =
      Path.of("target", "ui-test-results", "long-term-assets");
  private static final long PORTFOLIO_ID = HappyInvestorTestData.PORTFOLIO_ID;

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

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
  @DisplayName("canonical property flow preserves facts and rental contract editing")
  void canonicalPropertyFlowPreservesFactsAndRentalContracts() throws IOException {
    try (BrowserContext context = authenticatedContext()) {
      context
          .tracing()
          .start(
              new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
      Page page = context.newPage();
      try {
        open(page, "/portfolios/" + PORTFOLIO_ID + "/long-term-assets");

        var cashReserve = page.locator("#cash-reserves");
        assertThat(cashReserve.textContent())
            .contains("Cash reserve", "Value", "Net income", "Net yield", "Tax / year");

        page.locator("#real-estate .iv-planning-section__header").click();
        assertThat(page.locator("#real-estate").textContent())
            .contains("Rent tax / month", "267", "250", "517");
        page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions()
                    .setName(HappyInvestorTestData.APARTMENT_A_NAME)
                    .setExact(true))
            .click();
        assertCanonicalPropertyUrl(page, HappyInvestorLongTermFacts.APARTMENT_A_ID);
        assertThat(page.locator("body").textContent())
            .contains(
                HappyInvestorTestData.APARTMENT_A_NAME,
                "Monthly net income",
                "Income yield",
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
                "Other income",
                "Other expense");

        page.locator("#property-settings").evaluate("element => element.open = true");
        assertThat(page.locator("label[for='tax-base']")).hasText("Annual rental tax base");
        assertThat(page.locator("#tax-base").inputValue()).isEqualTo("3200");
        assertThat(page.locator("#property-value").inputValue()).isEqualTo("400000");
        assertThat(page.locator("#purchase-date").inputValue())
            .isEqualTo(HappyInvestorLongTermFacts.ACQUISITION_DATE.toString());
        assertThat(page.locator("#land-register-number").inputValue()).isEqualTo("KR1P/4322432/0");
        assertThat(page.locator("#property-notes").inputValue())
            .isEqualTo("Happy Investor canonical profile");

        page.locator("#property-name").fill(HappyInvestorTestData.APARTMENT_A_NAME + " UI");
        submit(page, page.locator("#property-settings button[type='submit']"));
        assertCanonicalPropertyUrl(page, HappyInvestorLongTermFacts.APARTMENT_A_ID);
        assertThat(page.locator(".iv-property-hero h1"))
            .hasText(HappyInvestorTestData.APARTMENT_A_NAME + " UI");
        assertThat(
                jdbc.queryForMap(
                    "SELECT acquisition_date, land_register_number, notes FROM investory.real_estate WHERE id = ?",
                    HappyInvestorLongTermFacts.APARTMENT_A_ID))
            .containsEntry("land_register_number", "KR1P/4322432/0")
            .containsEntry("notes", "Happy Investor canonical profile")
            .containsEntry(
                "acquisition_date",
                java.sql.Date.valueOf(HappyInvestorLongTermFacts.ACQUISITION_DATE));
        assertThat(
                jdbc.queryForObject(
                    "SELECT tax_base FROM investory.real_estate WHERE id = ?",
                    BigDecimal.class,
                    HappyInvestorLongTermFacts.APARTMENT_A_ID))
            .isEqualByComparingTo(HappyInvestorLongTermFacts.APARTMENT_A_ANNUAL_TAX_BASE);

        page.locator("#property-settings").evaluate("element => element.open = true");
        page.locator("#property-name").fill(HappyInvestorTestData.APARTMENT_A_NAME);
        submit(page, page.locator("#property-settings button[type='submit']"));
        assertCanonicalPropertyUrl(page, HappyInvestorLongTermFacts.APARTMENT_A_ID);
        assertThat(page.locator(".iv-property-hero h1"))
            .hasText(HappyInvestorTestData.APARTMENT_A_NAME);

        page.locator("[data-edit-contract]").first().click();
        var edit = page.locator("[data-contract-edit]").first();
        edit.locator("[name='otherIncome']")
            .fill(HappyInvestorTestData.APARTMENT_A_MONTHLY_RENT.toPlainString());
        edit.locator("[name='otherExpense']")
            .fill(HappyInvestorTestData.APARTMENT_B1_MONTHLY_RENT.toPlainString());
        edit.locator("[name='otherExpensePaidByTenant']").selectOption("true");
        submit(page, edit.locator("button[type='submit']"));
        assertCanonicalPropertyUrl(page, HappyInvestorLongTermFacts.APARTMENT_A_ID);
        assertThat(page.locator("[data-contract-read]").first()).containsText("Paid by tenant");
        assertThat(page.locator("[data-contract-read]").first().textContent())
            .contains("Other income", "Other expense", "Paid by tenant");

        page.locator("[data-edit-contract]").first().click();
        edit = page.locator("[data-contract-edit]").first();
        edit.locator("[name='otherIncome']").fill("");
        edit.locator("[name='otherExpense']").fill("");
        edit.locator("[name='otherExpensePaidByTenant']").selectOption("false");
        submit(page, edit.locator("button[type='submit']"));
        assertCanonicalPropertyUrl(page, HappyInvestorLongTermFacts.APARTMENT_A_ID);
        assertThat(page.locator("[data-contract-read]").first()).not().containsText("Other income");
        assertThat(page.locator("[data-contract-read]").first())
            .not()
            .containsText("Other expense");

        open(
            page,
            "/portfolios/"
                + PORTFOLIO_ID
                + "/long-term-assets/"
                + HappyInvestorLongTermFacts.APARTMENT_A_ID
                + "/real-estate#rental-contracts");
        assertCanonicalPropertyUrl(page, HappyInvestorLongTermFacts.APARTMENT_A_ID);

        open(page, "/portfolios/" + PORTFOLIO_ID + "/long-term-assets/new/real-estate");
        assertThat(page.locator("main").getAttribute("class"))
            .contains("iv-app", "iv-planning-page");
        page.locator("#property-create-name")
            .fill(HappyInvestorTestData.APARTMENT_A_NAME + " copy");
        page.locator("#property-create-currency")
            .selectOption(HappyInvestorTestData.REPORTING_CURRENCY.name());
        page.locator("#property-create-value")
            .fill(HappyInvestorTestData.APARTMENT_A_VALUE.toPlainString());
        page.locator("#property-create-date")
            .fill(HappyInvestorLongTermFacts.ACQUISITION_DATE.toString());
        page.locator("#property-create-register").fill("KR1P/4322432/0-UI");
        page.locator("#property-create-notes").fill("Happy Investor canonical profile copy");
        submit(
            page,
            page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Save property").setExact(true)));
        page.waitForURL(Pattern.compile(".*/portfolios/1/long-term-assets/[0-9]+/real-estate$"));
        assertThat(page.locator(".iv-property-hero h1"))
            .hasText(HappyInvestorTestData.APARTMENT_A_NAME + " copy");
        assertThat(page.locator("body").textContent())
            .contains(HappyInvestorTestData.APARTMENT_A_NAME + " copy", "Rental contracts");

        context.tracing().stop();
      } catch (AssertionError | RuntimeException failure) {
        saveFailureArtifacts(page, context);
        throw failure;
      }
    }
  }

  private void open(Page page, String path) {
    String target = baseUrl() + path;
    String previousUrl = page.url();
    var response = page.navigate(target);
    if (response == null) {
      // Playwright returns no response for same-document fragment navigation only.
      assertThat(target).contains("#");
      assertThat(previousUrl.split("#", 2)[0]).isEqualTo(target.split("#", 2)[0]);
      assertThat(page.url()).isEqualTo(target);
      assertThat(page.locator("#rental-contracts")).isVisible();
      return;
    }
    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo(200);
  }

  private void submit(Page page, Locator button) {
    // A same-URL redirect can satisfy waitForURL before the POST has even finished.
    String action = button.locator("xpath=ancestor::form").getAttribute("action");
    var response =
        page.waitForResponse(
            candidate ->
                candidate.request().method().equals("POST")
                    && candidate
                        .url()
                        .equals(java.net.URI.create(page.url()).resolve(action).toString()),
            button::click);
    assertThat(response.status()).isBetween(300, 399);
  }

  private void assertCanonicalPropertyUrl(Page page, long assetId) {
    page.waitForURL(
        Pattern.compile(
            ".*/portfolios/"
                + PORTFOLIO_ID
                + "/long-term-assets/"
                + assetId
                + "/real-estate(?:#rental-contracts)?$"));
    assertThat(page.locator(".iv-property-hero")).isVisible();
  }

  private BrowserContext authenticatedContext() {
    BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setHttpCredentials("admin", "change-me-admin")
                .setViewportSize(1440, 1000));
    context.setDefaultTimeout(Duration.ofSeconds(20).toMillis());
    context.setDefaultNavigationTimeout(Duration.ofSeconds(20).toMillis());
    return context;
  }

  private void saveFailureArtifacts(Page page, BrowserContext context) throws IOException {
    Files.createDirectories(ARTIFACT_DIRECTORY);
    page.screenshot(
        new Page.ScreenshotOptions()
            .setPath(ARTIFACT_DIRECTORY.resolve("canonical-property-flow.png"))
            .setFullPage(true));
    Files.writeString(ARTIFACT_DIRECTORY.resolve("canonical-property-flow.html"), page.content());
    context
        .tracing()
        .stop(
            new Tracing.StopOptions()
                .setPath(ARTIFACT_DIRECTORY.resolve("canonical-property-flow-trace.zip")));
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }
}
