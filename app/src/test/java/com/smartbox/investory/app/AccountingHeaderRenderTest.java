package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

class AccountingHeaderRenderTest {
  @Test
  void rendersAccountingScorecardsAndDueDateTotalAndStatusInSharedHeader() throws Exception {
    String source =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/accounting/accounting.html"));
    String controls =
        Files.readString(
            Path.of(
                "../adapters/web-ui/src/main/resources/templates/accounting/fragments/header-controls.html"));
    assertThat(source)
        .doesNotContain(
            "data-testid=\"accounting-summary\"",
            "data-testid=\"accounting-staging\"",
            "data-testid=\"accounting-reference-comparison\"",
            "data-testid=\"accounting-payments\"",
            "data-testid=\"accounting-actions\"",
            "th:fragment=\"accountingHeaderActions\"",
            "th:fragment=\"accountingHeaderDetails\"",
            "th:fragment=\"accountingMonthSelector\"");
    int headerStart = source.indexOf("<div th:replace=\"~{fragments/app-header :: appHeader(");
    int headerEnd = source.indexOf("</div>", headerStart) + "</div>".length();
    int actionsStart = controls.indexOf("<th:block th:fragment=\"accountingHeaderActions\">");
    int actionsEnd = controls.indexOf("</th:block>", actionsStart) + "</th:block>".length();
    int detailsStart = controls.indexOf("<th:block th:fragment=\"accountingHeaderDetails\">");
    int detailsEnd = controls.indexOf("</th:block>", detailsStart) + "</th:block>".length();
    int monthStart = controls.indexOf("<th:block th:fragment=\"accountingMonthSelector\">");
    int monthEnd = controls.indexOf("</th:block>", monthStart) + "</th:block>".length();
    String template =
        source
                .substring(headerStart, headerEnd)
                .replace("accounting/fragments/header-controls", "")
                .replace(" :: accountingHeaderActions", "::accountingHeaderActions")
                .replace(" :: accountingHeaderDetails", "::accountingHeaderDetails")
                .replace(" :: accountingMonthSelector", "::accountingMonthSelector")
            + controls.substring(actionsStart, actionsEnd)
            + controls.substring(detailsStart, detailsEnd)
            + controls.substring(monthStart, monthEnd);

    String html = templateEngine().process(template, context());

    assertThat(html)
        .contains(
            "Ryczałt",
            "100.00 zł",
            "VAT",
            "200.00 zł",
            "ZUS",
            "300.00 zł",
            "Bank · 100.00 PLN",
            "✓ Paid",
            "Bank · 200.00 PLN",
            "⚠ Difference",
            "For 2026-09",
            "600.00 zł",
            "Due date",
            "2026-08-20",
            "Status",
            "In progress",
            "Sync KSeF",
            "Import bank",
            "Import document",
            "accounting-month-prev",
            "accounting-month-next",
            "September 2026",
            "ksef/sync",
            "bank/import",
            "documents/recognize")
        .doesNotContain("Next action", "Choose document");
  }

  @Test
  void incomeAndCostsUseInitiallyCollapsedLongTermAssetDisclosureStyle() throws Exception {
    String source =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/accounting/accounting.html"));

    assertThat(source)
        .contains(
            "class=\"iv-planning-section iv-card card accounting-section\" data-testid=\"accounting-income\"",
            "class=\"iv-planning-section iv-card card accounting-section\" data-testid=\"accounting-costs\"",
            "class=\"iv-planning-section__details\"",
            "aria-label=\"Collapsed income summary\"",
            "aria-label=\"Collapsed costs summary\"")
        .doesNotContain("<details class=\"iv-planning-section__details\" open>");
  }

  private static WebContext context() {
    var webApplication = JakartaServletWebApplication.buildApplication(new MockServletContext());
    Map<String, Object> overview =
        Map.of(
            "summary",
            Map.of(
                "ryczalt", new BigDecimal("100.00"),
                "vat", new BigDecimal("200.00"),
                "zus", new BigDecimal("300.00")),
            "reference",
            Map.of(
                "available", true,
                "ryczalt", new BigDecimal("101.00"),
                "vatPayable", new BigDecimal("201.00"),
                "zus", new BigDecimal("301.00")));
    return new WebContext(
        webApplication.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()),
        java.util.Locale.US,
        Map.ofEntries(
            Map.entry("overview", overview),
            Map.entry("ryczaltDisplay", "100.00 zł"),
            Map.entry("vatDisplay", "200.00 zł"),
            Map.entry("zusDisplay", "300.00 zł"),
            Map.entry("bankRyczaltDisplay", "100.00 PLN"),
            Map.entry("bankVatDisplay", "200.00 PLN"),
            Map.entry("bankZusDisplay", "—"),
            Map.entry("bankRyczaltStatus", "✓ Paid"),
            Map.entry("bankVatStatus", "⚠ Difference"),
            Map.entry("bankZusStatus", "○ Unpaid"),
            Map.entry("bankRyczaltStatusClass", "is-paid"),
            Map.entry("bankVatStatusClass", "is-diff"),
            Map.entry("bankZusStatusClass", "is-unpaid"),
            Map.entry("profileId", 1L),
            Map.entry("selectedMonth", java.time.YearMonth.of(2026, 9)),
            Map.entry(
                "months",
                java.util.List.of(
                    Map.of("month", java.time.YearMonth.of(2026, 8), "label", "August 2026"),
                    Map.of("month", java.time.YearMonth.of(2026, 9), "label", "September 2026"),
                    Map.of("month", java.time.YearMonth.of(2026, 10), "label", "October 2026"))),
            Map.entry("previousMonth", Map.of("month", java.time.YearMonth.of(2026, 8))),
            Map.entry("nextMonth", Map.of("month", java.time.YearMonth.of(2026, 10))),
            Map.entry("payments", java.util.List.of(Map.of("dueDate", "2026-08-20"))),
            Map.entry("totalToPay", new BigDecimal("600.00")),
            Map.entry("totalToPayDisplay", "600.00 zł"),
            Map.entry("workspaceStatus", "In progress"),
            Map.entry("canWrite", true)));
  }

  private static TemplateEngine templateEngine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
    resolver.setCheckExistence(true);
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    StringTemplateResolver inline = new StringTemplateResolver();
    inline.setTemplateMode(TemplateMode.HTML);
    inline.setCacheable(false);
    engine.addTemplateResolver(inline);
    return engine;
  }
}
