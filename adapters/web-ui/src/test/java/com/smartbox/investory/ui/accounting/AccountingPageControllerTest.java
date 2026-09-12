package com.smartbox.investory.ui.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
  void initialPageUsesOnlyMonthsAndOverview() {
    var overview = mock(AccountingRestClient.MonthOverview.class);
    when(client.months(1))
        .thenReturn(
            List.of(new AccountingRestClient.MonthRef(month, "March 2026", "OPEN", "Open")));
    when(client.overview(1, month)).thenReturn(overview);

    var model = new ExtendedModelMap();
    assertThat(controller.page(1, month, model, new MockHttpServletRequest()))
        .isEqualTo("accounting/accounting");

    verify(client).months(1);
    verify(client).overview(1, month);
    verify(client, never()).documents(anyLong(), any());
    verify(client, never()).bankTransactions(anyLong(), any());
    verify(client, never()).payments(anyLong(), any());
    verify(client, never()).filings(anyLong(), any());
    verify(client, never()).reconciliation(anyLong(), any());
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
