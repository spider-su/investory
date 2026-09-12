package com.smartbox.investory.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.smartbox.investory.accounting.testsupport.AccountingDatabaseTest;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingPocUiIT extends AccountingDatabaseTest {
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
  void switchesMonthTogglesUopAndShowsReconciliation() {
    try (BrowserContext context = context();
        Page page = context.newPage()) {
      page.navigate(baseUrl() + "/poc/accounting?month=2026-02");
      assertThat(page.locator("#month")).hasValue("2026-02");
      assertThat(page.getByText("Reconciliation", new Page.GetByTextOptions().setExact(true)))
          .isVisible();
      assertThat(zusRow(page, "JDG social ZUS")).hasText("0.00");
      assertThat(zusRow(page, "JDG health ZUS")).hasText("1495.04");
      assertThat(zusRow(page, "Total JDG ZUS")).hasText("1495.04");
      assertThat(zusRow(page, "Social ZUS basis")).containsText("Qualifying UoP");

      page.waitForNavigation(() -> page.locator("#month").selectOption("2026-07"));
      org.assertj.core.api.Assertions.assertThat(page.url())
          .contains("/poc/accounting?month=2026-07");
      assertThat(page.locator("#month")).hasValue("2026-07");

      page.locator("input[name='hasUop']").uncheck();
      page.waitForNavigation(
          () ->
              page.locator("form[action='/poc/accounting/profile'] button[type='submit']").click());
      org.assertj.core.api.Assertions.assertThat(page.url())
          .contains("/poc/accounting?month=2026-07");
      assertThat(page.getByText("UoP disabled.", new Page.GetByTextOptions().setExact(false)))
          .isVisible();
      assertThat(
              page.getByText("HISTORICAL_PROFILE_DIFF", new Page.GetByTextOptions().setExact(true)))
          .isVisible();
      assertThat(zusRow(page, "JDG social ZUS")).hasText("1788.29");
      assertThat(zusRow(page, "Total JDG ZUS")).hasText("3283.33");
      assertThat(zusRow(page, "Social ZUS basis")).containsText("JDG is the primary");

      page.waitForNavigation(() -> page.locator("#month").selectOption("2026-02"));
      assertThat(page.locator("#month")).hasValue("2026-02");
      assertThat(zusRow(page, "JDG social ZUS")).hasText("1788.29");
      assertThat(zusRow(page, "Social ZUS basis")).containsText("JDG is the primary");

      page.setInputFiles(
          "#invoice-file",
          new FilePayload("reviewed-invoice.pdf", "application/pdf", invoicePdf()));
      page.waitForNavigation(
          () ->
              page.getByRole(
                      AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Recognize & autofill"))
                  .click());
      assertThat(
              page.getByRole(
                  AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save reviewed invoice")))
          .isVisible();
      page.waitForNavigation(
          () ->
              page.getByRole(
                      AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save reviewed invoice"))
                  .click());
      assertThat(
              page.getByText("Purchase invoice saved.", new Page.GetByTextOptions().setExact(true)))
          .isVisible();

      page.setInputFiles(
          "#invoice-file",
          new FilePayload("reviewed-invoice.pdf", "application/pdf", invoicePdf()));
      page.waitForNavigation(
          () ->
              page.getByRole(
                      AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Recognize & autofill"))
                  .click());
      page.waitForNavigation(
          () ->
              page.getByRole(
                      AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save reviewed invoice"))
                  .click());
      assertThat(
              page.getByText(
                  "Invoice was already imported; no duplicate was created.",
                  new Page.GetByTextOptions().setExact(true)))
          .isVisible();
    }
  }

  private Locator zusRow(Page page, String label) {
    return page.locator("section")
        .filter(new Locator.FilterOptions().setHasText("ZUS trace"))
        .locator("tr")
        .filter(new Locator.FilterOptions().setHasText(label))
        .locator("td")
        .first();
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
              "Faktura VAT: UI-2026-07",
              "Data wystawienia: 10.07.2026",
              "Sprzedawca: Demo Supplier NIP: 1234567890",
              "Nabywca: Demo Buyer NIP: 9876543210",
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
