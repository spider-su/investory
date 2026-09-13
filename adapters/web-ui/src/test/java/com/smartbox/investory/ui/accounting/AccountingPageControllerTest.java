package com.smartbox.investory.ui.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

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
  void legacyRouteRedirectsToProfileScopedAccounting() {
    assertThat(controller.legacyPage(1, month))
        .isEqualTo("redirect:/profiles/1/accounting?month=2026-03");
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
    assertThat(model.get("workspaceNextAction")).isEqualTo("Add source data");

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
  void acquiredStagingRowsPresentReviewAsTheNextStep() {
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview(0, 0, 1, 0, 0, false));
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 1, 0, 0, 0, 0, 0, 0, 1, 0));
    when(client.rows(1, month))
        .thenReturn(
            List.of(
                new AccountingStagingApi.Row(
                    "INVOICE",
                    1,
                    "INV-1",
                    "UPLOAD",
                    "NEW",
                    List.of(),
                    null,
                    BigDecimal.TEN,
                    "PLN",
                    false)));

    var model = new ExtendedModelMap();
    controller.page(1, month, model, new MockHttpServletRequest());

    assertThat(model.get("hasAcquiredData")).isEqualTo(true);
    assertThat(model.get("hasOperationalData")).isEqualTo(false);
    assertThat(model.get("workspaceStatus")).isEqualTo("In progress");
    assertThat(model.get("workspaceNextAction")).isEqualTo("Promote ready data");
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
    assertThat(model.get("workspaceNextAction")).isEqualTo("Review issues");
    assertThat(model.get("hasReviewIssues")).isEqualTo(true);
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
        new AccountingRestClient.Summary(zero, zero, zero, zero, documents, bankTransactions),
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
