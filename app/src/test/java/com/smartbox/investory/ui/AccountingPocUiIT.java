package com.smartbox.investory.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FilePayload;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.accounting.AccountingDatabase;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Disabled
/** Browser coverage for the profile-scoped accounting workspace and reviewed upload flow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-fast")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingPocUiIT {
  private static final WorkerDatabase DATABASE = AccountingDatabase.scopedPocDatabase("ui");

  @Value("${local.server.port}")
  private int port;

  private Playwright playwright;
  private Browser browser;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @BeforeAll
  void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  void closeResources() {
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
    DATABASE.close();
  }

  @Test
  void rendersWorkspaceAndSwitchesAccountingMonth() {
    try (BrowserContext context = context();
        Page page = context.newPage()) {
      var response = page.navigate(baseUrl() + "/profiles/1/accounting?month=2026-02");

      assertThat(response.status()).isEqualTo(200);
      assertThat(page.getByTestId("accounting-page")).isVisible();
      assertThat(page.getByTestId("accounting-month")).hasValue("2026-02");
      assertThat(page.getByTestId("accounting-reference-comparison")).hasCount(0);
      assertThat(page.locator(".iv-shared-topbar").getByTestId("ksef-sync-all")).isVisible();
      assertThat(page.locator(".iv-shared-topbar").getByTestId("accounting-bank-import"))
          .isVisible();
      assertThat(page.locator(".iv-shared-topbar__kpi").allTextContents().toString())
          .contains("Ryczałt", "VAT", "ZUS");
      assertThat(page.getByTestId("accounting-obligations")).hasCount(0);
      assertThat(page.locator("[data-testid^=ksef-]")).hasCount(1);

      page.waitForNavigation(() -> page.getByTestId("accounting-month").selectOption("2026-07"));

      assertThat(page.url()).contains("/profiles/1/accounting?month=2026-07");
      assertThat(page.getByTestId("accounting-month")).hasValue("2026-07");
    }
  }

  @Test
  void recognizesAndStagesReviewedDocumentThroughCurrentUi() {
    try (BrowserContext context = context();
        Page page = context.newPage()) {
      page.navigate(baseUrl() + "/profiles/1/accounting?month=2026-02");
      page.waitForNavigation(
          () ->
              page.getByTestId("accounting-document-file")
                  .setInputFiles(
                      new FilePayload("reviewed-invoice.pdf", "application/pdf", invoicePdf())));

      assertThat(page.getByTestId("accounting-review-form")).isVisible();
      page.getByTestId("review-document-type").selectOption("PURCHASE_INVOICE");
      page.getByTestId("review-vat-treatment").selectOption("DOMESTIC_PURCHASE");
      page.getByTestId("review-category").fill("EQUIPMENT");
      page.getByTestId("review-counterparty-country").fill("PL");
      page.getByTestId("review-vat-rate").fill("23");
      page.getByTestId("review-vat-deduction-ratio").selectOption("1.00");
      String invalidFields =
          (String)
              page.locator("[data-testid=accounting-review-form]")
                  .evaluate(
                      "form => [...form.elements].filter(field => !field.checkValidity()).map(field => field.name).join(',')");
      assertThat(invalidFields).isEmpty();
      page.waitForNavigation(() -> page.getByTestId("review-stage-submit").click());

      assertThat(page.url()).contains("/profiles/1/accounting?month=2026-02");
      assertThat(page.getByText("Document staged for reconciliation.")).isVisible();
      assertThat(page.getByTestId("accounting-staging")).hasCount(0);
      assertThat(page.getByTestId("accounting-reconcile")).hasCount(0);
    }
  }

  private byte[] invoicePdf() {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage());
      try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        content.newLineAtOffset(50, 750);
        for (String line :
            new String[] {
              "Faktura VAT: UI-2026-02-REVIEW",
              "Data wystawienia: 10.02.2026",
              "Sprzedawca: Demo Supplier NIP: 1234567890",
              "Nabywca: Investory Accounting POC NIP: 1010000000",
              "Netto: 100,00 PLN",
              "VAT 23%: 23,00 PLN",
              "Brutto: 123,00 PLN"
            }) {
          content.showText(line);
          content.newLineAtOffset(0, -16);
        }
        content.endText();
      }
      document.save(output);
      return output.toByteArray();
    } catch (Exception exception) {
      throw new IllegalStateException("Could not create browser invoice fixture", exception);
    }
  }

  private BrowserContext context() {
    BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setHttpCredentials("admin", "change-me-admin")
                .setViewportSize(2560, 1440));
    context.setDefaultTimeout(Duration.ofSeconds(20).toMillis());
    context.setDefaultNavigationTimeout(Duration.ofSeconds(20).toMillis());
    return context;
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }
}
