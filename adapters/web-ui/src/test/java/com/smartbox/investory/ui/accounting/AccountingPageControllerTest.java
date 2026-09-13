package com.smartbox.investory.ui.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
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
  void pageIncludesStagingRowsAndSummary() {
    var overview = mock(AccountingRestClient.MonthOverview.class);
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview);
    when(client.summary(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 1, 0, 0, 0, 0, 0, 0, 1, 0));
    when(client.rows(1, month)).thenReturn(List.of());

    var model = new ExtendedModelMap();
    assertThat(controller.page(1, month, model, new MockHttpServletRequest()))
        .isEqualTo("accounting/accounting");

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
  void stagingActionsUseClientAndRedirect() {
    when(client.reconcile(1, month))
        .thenReturn(new AccountingStagingApi.Summary(0, 1, 0, 0, 0, 0, 0, 0, 1, 0));
    when(client.promote(1, month)).thenReturn(new AccountingStagingApi.Promotion(1, 0));

    var reconcile = new RedirectAttributesModelMap();
    var promote = new RedirectAttributesModelMap();
    assertThat(controller.reconcile(1, month, reconcile))
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");
    assertThat(controller.promote(1, month, promote))
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");

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
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");
    assertThat(controller.file(1, month, redirect))
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");
    assertThat(controller.settle(1, month, redirect))
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");
    assertThat(controller.lock(1, month, redirect))
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");
    assertThat(controller.reopen(1, month, "correction", redirect))
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");

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
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");

    verify(client, never()).reopen(anyLong(), any(), anyString());
    assertThat(redirect.getFlashAttributes()).containsKey("accountingError");
  }

  @Test
  void ksefSyncUsesClientAndRedirects() {
    when(client.syncKsef(1, month))
        .thenReturn(new AccountingRestClient.KsefSyncResult("COMPLETED", 1, 1, 0, 0, 0, "done"));

    assertThat(controller.syncKsef(1, month, new RedirectAttributesModelMap()))
        .isEqualTo("redirect:/accounting?profileId=1&month=2026-03");
    verify(client).syncKsef(1, month);
  }
}
