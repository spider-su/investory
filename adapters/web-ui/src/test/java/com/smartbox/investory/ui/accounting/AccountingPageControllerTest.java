package com.smartbox.investory.ui.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.accounting.api.AccountingUserApi;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(MockitoExtension.class)
class AccountingPageControllerTest {
  @Mock private AccountingRestClient client;
  private AccountingPageController controller;
  private final YearMonth month = YearMonth.of(2026, 3);

  @BeforeEach
  void setUp() {
    controller = new AccountingPageController(client);
  }

  @Test
  void untouchedMonthIsPresentedAsWaitingForSourceData() {
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview(0, 0, 0, 0, 0, false));
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    when(client.rows(1, month)).thenReturn(List.of());
    var model = new ExtendedModelMap();
    assertThat(controller.page(1, month, model, new MockHttpServletRequest()))
        .isEqualTo("accounting/accounting");

    assertThat(model.get("hasAcquiredData")).isEqualTo(false);
    assertThat(model.get("hasOperationalData")).isEqualTo(false);
    assertThat(model.get("workspaceStatus")).isEqualTo("Waiting for data");
    assertThat(model.get("sourcesStepClass")).isEqualTo("accounting-workflow__step--pending");
    assertThat(model.get("reviewStepClass")).isEqualTo("accounting-workflow__step--pending");

    verify(client).months(1);
    verify(client).overview(1, month);
    verify(client).summary(1, month);
    verify(client).rows(1, month);
    verify(client, never()).documents(anyLong(), any());
    verify(client, never()).bankTransactions(anyLong(), any());
    verify(client, never()).payments(anyLong(), any());
    verify(client, never()).filings(anyLong(), any());
    verify(client, never()).reconciliation(anyLong(), any());
  }

  @Test
  void defaultsToCurrentCalendarMonthAndAddsItToTheSelector() {
    var current = YearMonth.now();
    var lastPeriod = current.minusMonths(1);
    when(client.months(1))
        .thenReturn(
            List.of(
                new AccountingRestClient.MonthRef(lastPeriod, "Previous month", "OPEN", "Open")));
    when(client.overview(1, current)).thenReturn(overview(0, 0, 0, 0, 0, false));
    when(client.summary(1, current))
        .thenReturn(new AccountingStagingApi.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    when(client.rows(1, current)).thenReturn(List.of());

    var model = new ExtendedModelMap();
    controller.page(1, null, model, new MockHttpServletRequest());

    assertThat(model.get("selectedMonth")).isEqualTo(current);
    assertThat((List<AccountingRestClient.MonthRef>) model.get("months"))
        .extracting(AccountingRestClient.MonthRef::month)
        .contains(current);
  }

  @Test
  void monthNavigationUsesAvailablePeriodsAndStopsAtTheEnds() {
    var february = month.minusMonths(1);
    var april = month.plusMonths(1);
    when(client.months(1))
        .thenReturn(
            List.of(
                new AccountingRestClient.MonthRef(february, "February 2026", "OPEN", "Open"),
                new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open"),
                new AccountingRestClient.MonthRef(april, "April 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview(0, 0, 0, 0, 0, false));
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    when(client.rows(1, month)).thenReturn(List.of());

    var model = new ExtendedModelMap();
    controller.page(1, month, model, new MockHttpServletRequest());

    assertThat(((AccountingRestClient.MonthRef) model.get("previousMonth")).month())
        .isEqualTo(february);
    assertThat(((AccountingRestClient.MonthRef) model.get("nextMonth")).month()).isEqualTo(april);
  }

  @Test
  void acquiredStagingRowsPresentReviewAsTheNextStep() {
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview(0, 0, 1, 0, 0, false));
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 1, 0, 0, 0, 0, 0, 0, 1, 0));
    when(client.rows(1, month)).thenReturn(List.of());

    var model = new ExtendedModelMap();
    controller.page(1, month, model, new MockHttpServletRequest());

    assertThat(model.get("hasAcquiredData")).isEqualTo(true);
    assertThat(model.get("hasOperationalData")).isEqualTo(false);
    assertThat(model.get("workspaceStatus")).isEqualTo("In progress");
  }

  @Test
  void blockingAcquisitionIsPresentedAsReviewNeeded() {
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview(0, 0, 1, 1, 0, false));
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 0, 1, 0, 0, 0, 0, 0, 0, 1));
    when(client.rows(1, month))
        .thenReturn(
            List.of(
                new AccountingStagingApi.Row(
                    "INVOICE",
                    1,
                    "INV-1",
                    "UPLOAD",
                    "MISMATCH",
                    List.of("AMOUNT_MISMATCH"),
                    10L,
                    BigDecimal.TEN,
                    "PLN",
                    false)));

    var model = new ExtendedModelMap();
    controller.page(1, month, model, new MockHttpServletRequest());

    assertThat(model.get("workspaceStatus")).isEqualTo("Review needed");
    assertThat(model.get("hasReviewIssues")).isEqualTo(true);
  }

  @Test
  void accountingRowsUsePolishMoneyDatesAndKeepIncomeSeparateFromCosts() {
    var base = overview(2, 0, 2, 1, 0, false);
    var summary =
        new AccountingUserApi.Summary(
            new BigDecimal("7636"),
            new BigDecimal("7407"),
            new BigDecimal("7754"),
            new BigDecimal("1495.04"),
            2,
            0);
    var overview =
        new AccountingRestClient.MonthOverview(
            base.month(),
            base.lifecycle(),
            base.lifecycleLabel(),
            base.nextAction(),
            base.nextActionLabel(),
            summary,
            base.issues(),
            base.sources(),
            base.ksefStatus(),
            base.documentSummary(),
            base.bankSummary(),
            base.paymentSummary(),
            base.filingSummary(),
            base.reconciliationSummary(),
            base.allowedActions(),
            base.reference());
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview);
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    when(client.rows(1, month))
        .thenReturn(
            List.of(
                new AccountingStagingApi.Row(
                    "INVOICE",
                    30,
                    "KSEF-030",
                    "9492107026-20260620-6EC2E8400001-B6",
                    "REVIEW_REQUIRED",
                    List.of(),
                    null,
                    new BigDecimal("250"),
                    "PLN",
                    false,
                    "KSEF",
                    "SALES_INVOICE",
                    java.time.LocalDate.of(2026, 6, 20),
                    "Acme",
                    null,
                    null,
                    null)));
    when(client.documents(1, month))
        .thenReturn(
            List.of(
                new AccountingRestClient.DocumentView(
                    1,
                    "INV-020",
                    "SALE",
                    java.time.LocalDate.of(2026, 6, 30),
                    new BigDecimal("7636"),
                    "EUR",
                    "REVIEW_REQUIRED",
                    "source-1"),
                new AccountingRestClient.DocumentView(
                    3,
                    "INV-ISSUED",
                    "SALE",
                    java.time.LocalDate.of(2026, 6, 12),
                    new BigDecimal("41770.8"),
                    "PLN",
                    "IMPORTED",
                    "source-3"),
                new AccountingRestClient.DocumentView(
                    2,
                    "EXP-26394",
                    "PURCHASE",
                    java.time.LocalDate.of(2026, 6, 26),
                    new BigDecimal("363.5"),
                    "PLN",
                    "READY",
                    "source-2",
                    "Supplier sp. z o.o.",
                    "BUSINESS_SERVICE",
                    null,
                    "1234567890",
                    "PL",
                    "UPLOAD",
                    "invoice.pdf")));
    when(client.reconciliation(1, month))
        .thenReturn(
            List.of(
                new AccountingUserApi.ReconciliationView(
                    "INV-020",
                    "INVOICE_PAYMENT",
                    new BigDecimal("7636"),
                    new BigDecimal("7636"),
                    "MATCHED",
                    "Exact business receipt")));
    when(client.payments(1, month)).thenReturn(List.of());

    var model = new ExtendedModelMap();
    controller.page(1, month, model, new MockHttpServletRequest());

    assertThat(model.get("ryczaltDisplay")).isEqualTo("7 754 zł");
    assertThat(model.get("zusDisplay")).isEqualTo("1 495,04 zł");
    assertThat(model.get("incomeDocumentsView")).asList().hasSize(3);
    assertThat(model.get("incomeDocumentsView"))
        .asList()
        .filteredOn(
            document ->
                "KSEF-030"
                    .equals(((AccountingPageController.DocumentPresentation) document).reference()))
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document).extracting("counterparty").isEqualTo("Acme");
              assertThat(document).extracting("status").isEqualTo("To review");
              assertThat(document).extracting("amount").isEqualTo("250 PLN");
              assertThat(document).extracting("href").asString().contains("sourceReference=");
            });
    assertThat(model.get("incomeDocumentsView"))
        .asList()
        .filteredOn(
            document ->
                "INV-020"
                    .equals(((AccountingPageController.DocumentPresentation) document).reference()))
        .singleElement()
        .satisfies(document -> assertThat(document).extracting("status").isEqualTo("Paid"));
    assertThat(model.get("incomeDocumentsView"))
        .asList()
        .filteredOn(
            document ->
                "INV-ISSUED"
                    .equals(((AccountingPageController.DocumentPresentation) document).reference()))
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document).extracting("status").isEqualTo("Issued");
              assertThat(document).extracting("amount").isEqualTo("41 771 PLN");
            });
    assertThat(model.get("costDocumentsView"))
        .asList()
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document).extracting("date").isEqualTo("26 Jun");
              assertThat(document).extracting("amount").isEqualTo("364 PLN");
              assertThat(document).extracting("category").isEqualTo("Services");
              assertThat(document).extracting("status").isEqualTo("Approved");
            });
    assertThat(model.get("jpkStatusLabel")).isEqualTo("JPK not generated");
    assertThat(model.get("upoStatusLabel")).isEqualTo("UPO not generated");
  }

  @Test
  void missingCostClassificationAndCounterpartyAreSafeAndRequireReview() {
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview(1, 0, 1, 0, 0, false));
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    when(client.rows(1, month)).thenReturn(List.of());
    when(client.documents(1, month))
        .thenReturn(
            List.of(
                new AccountingRestClient.DocumentView(
                    8,
                    "EXP-UNKNOWN",
                    "PURCHASE",
                    null,
                    new BigDecimal("100.25"),
                    "PLN",
                    "IMPORTED",
                    "source-8",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "UPLOAD",
                    "invoice.pdf")));
    when(client.reconciliation(1, month)).thenReturn(List.of());
    when(client.payments(1, month)).thenReturn(List.of());

    var model = new ExtendedModelMap();
    controller.page(1, month, model, new MockHttpServletRequest());

    assertThat(model.get("costDocumentsView"))
        .asList()
        .singleElement()
        .satisfies(
            document -> {
              assertThat(document).extracting("counterparty").isEqualTo("—");
              assertThat(document).extracting("category").isEqualTo("—");
              assertThat(document).extracting("date").isEqualTo("—");
              assertThat(document).extracting("status").isEqualTo("To review");
            });
  }

  @Test
  void issueCodesBecomeUserLanguageAndSuccessfulInfoIsRemovedFromReviewQueue() {
    var base = overview(1, 0, 1, 1, 0, false);
    var issues =
        List.of(
            new AccountingUserApi.IssueView(
                "id-1",
                "MISSING_JPK_EVIDENCE_CLASSIFICATION",
                "WARNING",
                AccountingUserApi.IssueKind.NEEDS_ANSWER,
                "MISSING JPK EVIDENCE CLASSIFICATION",
                "MISSING_JPK_EVIDENCE_CLASSIFICATION: INV-020",
                "INV-020",
                new AccountingUserApi.Resolution.None("review")),
            new AccountingUserApi.IssueView(
                "id-2",
                "SOURCE_PARSED",
                "INFO",
                AccountingUserApi.IssueKind.INFO,
                "SOURCE PARSED",
                "Source parsed",
                null,
                new AccountingUserApi.Resolution.None("informational")));
    var over =
        new AccountingRestClient.MonthOverview(
            base.month(),
            base.lifecycle(),
            base.lifecycleLabel(),
            base.nextAction(),
            base.nextActionLabel(),
            base.summary(),
            issues,
            base.sources(),
            base.ksefStatus(),
            base.documentSummary(),
            base.bankSummary(),
            base.paymentSummary(),
            base.filingSummary(),
            base.reconciliationSummary(),
            base.allowedActions(),
            base.reference());
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(over);
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    when(client.rows(1, month)).thenReturn(List.of());

    var model = new ExtendedModelMap();
    controller.page(1, month, model, new MockHttpServletRequest());

    assertThat(model.get("reviewIssues"))
        .asList()
        .singleElement()
        .satisfies(
            issue -> {
              assertThat(issue).extracting("title").isEqualTo("JPK category required");
              assertThat(issue)
                  .extracting("message")
                  .isEqualTo("Choose the JPK category for this document.");
            });
  }

  @Test
  void accountingPageTemplateRendersWorkflowAndFilingStatuses() throws Exception {
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview(2, 0, 0, 0, 0, false));
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    when(client.rows(1, month)).thenReturn(List.of());
    var documents =
        List.of(
            new AccountingRestClient.DocumentView(
                21,
                "INV-021",
                "SALES",
                java.time.LocalDate.of(2026, 3, 20),
                new BigDecimal("1200"),
                "PLN",
                "IMPORTED",
                "source-21"),
            new AccountingRestClient.DocumentView(
                22,
                "EXP-022",
                "PURCHASE",
                java.time.LocalDate.of(2026, 3, 21),
                new BigDecimal("300"),
                "PLN",
                "IMPORTED",
                "source-22"));
    when(client.documents(1, month)).thenReturn(documents);
    when(client.reconciliation(1, month)).thenReturn(List.of());
    when(client.payments(1, month)).thenReturn(List.of());

    var resolver = new ThymeleafViewResolver();
    resolver.setTemplateEngine(templateEngine());
    resolver.setViewNames(new String[] {"accounting/*", "fragments/*"});
    MockMvc renderingMvc =
        MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();

    renderingMvc
        .perform(get("/profiles/1/accounting").param("month", "2026-03"))
        .andExpect(status().isOk())
        .andExpect(view().name("accounting/accounting"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Month readiness")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("JPK not generated")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("UPO not generated")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "/profiles/1/accounting/documents/21?month=2026-03")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "/profiles/1/accounting/documents/22?month=2026-03")));
    // Both domain aliases for sales remain in the Income section and link to detail.
    assertThat(
            renderingMvc
                .perform(get("/profiles/1/accounting").param("month", "2026-03"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .contains("1 document</span>");

    renderingMvc
        .perform(get("/profiles/1/accounting/documents/21").param("month", "2026-03"))
        .andExpect(status().isOk())
        .andExpect(view().name("accounting/document"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Document type")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("readonly")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("INV-021")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("1 200")));
  }

  private static SpringTemplateEngine templateEngine() {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
    resolver.setCheckExistence(true);
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }

  @Test
  void stagingActionsUseClientAndRedirect() {
    when(client.reconcile(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 1, 0, 0, 0, 0, 0, 0, 1, 0));
    when(client.promote(1, month)).thenReturn(new AccountingStagingApi.Promotion(1, 0));

    var reconcile = new RedirectAttributesModelMap();
    var promote = new RedirectAttributesModelMap();
    assertThat(controller.reconcile(1, month, reconcile))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");
    assertThat(controller.promote(1, month, promote))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");

    verify(client).reconcile(1, month);
    verify(client).promote(1, month);
    assertThat(reconcile.getFlashAttributes().get("accountingMessage"))
        .isEqualTo("Reconciliation completed: 1 row(s) ready to promote.");
    assertThat(promote.getFlashAttributes().get("accountingMessage"))
        .isEqualTo("Promoted 1 document(s) and 0 bank transaction(s).");
  }

  @Test
  void lifecycleActionsUseClientAndRedirect() {
    var redirect = new RedirectAttributesModelMap();

    assertThat(controller.confirm(1, month, redirect))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");
    assertThat(controller.file(1, month, redirect))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");
    assertThat(controller.settle(1, month, redirect))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");
    assertThat(controller.lock(1, month, redirect))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");
    assertThat(controller.reopen(1, month, "correction", redirect))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");

    verify(client).confirm(1, month);
    verify(client).file(1, month);
    verify(client).settle(1, month);
    verify(client).lock(1, month);
    verify(client).reopen(1, month, "correction");
  }

  @Test
  void blankReopenReasonDoesNotCallClient() {
    var redirect = new RedirectAttributesModelMap();

    assertThat(controller.reopen(1, month, " ", redirect))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");

    verify(client, never()).reopen(anyLong(), any(), anyString());
    assertThat(redirect.getFlashAttributes()).containsKey("accountingError");
  }

  @Test
  void ksefSyncUsesClientAndRedirects() {
    when(client.syncKsef(1, month))
        .thenReturn(new AccountingRestClient.KsefSyncResult("COMPLETED", 1, 1, 0, 0, 0, "done"));

    assertThat(controller.syncKsef(1, month, new RedirectAttributesModelMap()))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");
    verify(client).syncKsef(1, month);
  }

  @Test
  void ksefSyncUsesErrorForFailedResults() {
    when(client.syncKsef(1, month))
        .thenReturn(
            new AccountingRestClient.KsefSyncResult(
                "COMPLETED",
                2,
                0,
                0,
                1,
                1,
                "KSeF sync finished: 2 received, 0 imported, 0 duplicates, 1 need review, 1 failed."));

    var redirect = new RedirectAttributesModelMap();
    controller.syncKsef(1, month, redirect);

    assertThat(redirect.getFlashAttributes()).doesNotContainKey("accountingMessage");
    assertThat(redirect.getFlashAttributes().get("accountingError"))
        .isEqualTo(
            "KSeF sync finished: 2 received, 0 imported, 0 duplicates, 1 need review, 1 failed.");
  }

  @Test
  void ksefSyncUsesWarningForReviewResults() {
    when(client.syncKsef(1, month))
        .thenReturn(
            new AccountingRestClient.KsefSyncResult(
                "COMPLETED",
                1,
                0,
                0,
                1,
                0,
                "KSeF sync finished: 1 received, 0 imported, 0 duplicates, 1 need review, 0 failed."));

    var redirect = new RedirectAttributesModelMap();
    controller.syncKsef(1, month, redirect);

    assertThat(redirect.getFlashAttributes()).doesNotContainKey("accountingMessage");
    assertThat(redirect.getFlashAttributes().get("accountingWarning"))
        .isEqualTo(
            "KSeF sync finished: 1 received, 0 imported, 0 duplicates, 1 need review, 0 failed.");
  }

  private AccountingRestClient.MonthOverview overview(
      int documents,
      int bankTransactions,
      int evidence,
      int reviewRequired,
      int failed,
      boolean filingReady) {
    var zero = BigDecimal.ZERO;
    return new AccountingRestClient.MonthOverview(
        month,
        "OPEN",
        "Open",
        "REVIEW",
        "Review issues",
        new AccountingUserApi.Summary(zero, zero, zero, zero, documents, bankTransactions),
        List.of(),
        new AccountingRestClient.SourceSummary(evidence, 0, reviewRequired, failed),
        "CONNECTED",
        new AccountingRestClient.DocumentSummary(documents, 0, documents, reviewRequired, failed),
        new AccountingRestClient.BankSummary(
            bankTransactions, 0, bankTransactions > 0 ? "IMPORTED" : "NO_IMPORT"),
        new AccountingRestClient.PaymentSummary(0, 0, zero),
        new AccountingRestClient.FilingSummary(
            "OPEN", "Open", filingReady, List.of(), "MISSING", null, "MISSING", null, null),
        new AccountingRestClient.ReconciliationSummary(0, 0, 0, 0),
        List.of(),
        new AccountingRestClient.ReferenceSummary(
            true, zero, zero, zero, zero, zero, zero, zero, 0, 0, "OPEN"));
  }
}
