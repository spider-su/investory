package com.smartbox.investory.ryczalt.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.config.RestApiExceptionHandler;
import com.smartbox.investory.ryczalt.application.RyczaltAccountingApi;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodNotFoundException;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RyczaltAccountingRestControllerTest {
  private final RyczaltAccountingApi accounting = mock(RyczaltAccountingApi.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final Authentication authentication = mock(Authentication.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    when(authentication.getName()).thenReturn("owner");
    when(authorization.canRead(7L, authentication)).thenReturn(true);
    when(authorization.canWrite(7L, authentication)).thenReturn(true);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new RyczaltAccountingRestController(accounting, authorization))
            .setControllerAdvice(new RestApiExceptionHandler(mock(ApplicationTime.class)))
            .build();
  }

  @Test
  void periodsUseNativeQueryAndIsoYearMonth() throws Exception {
    when(accounting.periods(7L))
        .thenReturn(List.of(new RyczaltPeriodListItem(YearMonth.of(2026, 8), PeriodStatus.OPEN)));

    mvc.perform(get("/api/profiles/7/accounting/periods").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(content().json("[{\"month\":\"2026-08\",\"status\":\"OPEN\"}]"));

    verify(accounting).periods(7L);
  }

  @Test
  void invoiceMoneyIsSerializedAsDecimalString() throws Exception {
    when(accounting.invoices(7L, YearMonth.of(2026, 8)))
        .thenReturn(
            List.of(
                new RyczaltInvoiceReadModel(
                    11L,
                    InvoiceDirection.INCOME,
                    "FV/8",
                    LocalDate.of(2026, 8, 20),
                    LocalDate.of(2026, 8, 20),
                    new BigDecimal("1234.56"),
                    new BigDecimal("283.95"),
                    new BigDecimal("1518.51"),
                    CurrencyType.PLN,
                    new BigDecimal("1234.56"),
                    new BigDecimal("0.12"),
                    null)));

    mvc.perform(
            get("/api/profiles/7/accounting/periods/2026-08/invoices").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(content().json("[{\"netAmount\":\"1234.56\",\"issueDate\":\"2026-08-20\"}]"));
  }

  @Test
  void missingNativePeriodIsNotFoundAndNeverLegacyBacked() throws Exception {
    when(accounting.invoices(7L, YearMonth.of(2026, 8)))
        .thenThrow(new RyczaltPeriodNotFoundException(7L, YearMonth.of(2026, 8)));

    mvc.perform(
            get("/api/profiles/7/accounting/periods/2026-08/invoices").principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void invoiceHistoryFiltersAreOptional() throws Exception {
    when(accounting.invoices(7L, null, null)).thenReturn(List.of());

    mvc.perform(get("/api/profiles/7/accounting/invoices").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));

    verify(accounting).invoices(7L, null, null);
  }

  @Test
  void deniedProfileReturnsForbiddenBeforeNativeQuery() throws Exception {
    when(authorization.canRead(7L, authentication)).thenReturn(false);

    mvc.perform(get("/api/profiles/7/accounting/periods").principal(authentication))
        .andExpect(status().isForbidden());
  }

  @Test
  void reopenUsesNativeLifecycleAndExplicitReason() throws Exception {
    mvc.perform(
            post("/api/profiles/7/accounting/periods/2026-08/reopen")
                .principal(authentication)
                .contentType("application/json")
                .content("{\"reason\":\"Correction\"}"))
        .andExpect(status().isNoContent());

    verify(accounting).reopen(7L, YearMonth.of(2026, 8), "owner", "Correction");
  }

  @Test
  void allNativeReadRoutesAreExposedSeparatelyFromLegacyRoutes() throws Exception {
    YearMonth month = YearMonth.of(2026, 8);
    when(accounting.period(7L, month))
        .thenReturn(
            new RyczaltPeriodReadModel(
                month,
                PeriodStatus.OPEN,
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                RyczaltPeriodReadModel.ObligationTotals.empty(),
                new RyczaltPeriodReadModel.Completeness("COMPLETE", 0),
                Set.of()));
    when(accounting.invoices(7L, month)).thenReturn(List.of());
    when(accounting.transactions(7L, month)).thenReturn(List.of());
    when(accounting.obligations(7L, month)).thenReturn(List.of());
    when(accounting.issues(7L, month)).thenReturn(List.of());
    when(accounting.paymentHistory(7L, month, month, null)).thenReturn(List.of());

    mvc.perform(get("/api/profiles/7/accounting/periods/2026-08").principal(authentication))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/profiles/7/accounting/periods/2026-08/transactions")
                .principal(authentication))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/profiles/7/accounting/periods/2026-08/obligations").principal(authentication))
        .andExpect(status().isOk());
    mvc.perform(get("/api/profiles/7/accounting/periods/2026-08/issues").principal(authentication))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/profiles/7/accounting/payments")
                .param("from", "2026-08")
                .param("to", "2026-08")
                .principal(authentication))
        .andExpect(status().isOk());
  }

  @Test
  void settleAndFreezeUseNativeCommands() throws Exception {
    mvc.perform(post("/api/profiles/7/accounting/periods/2026-08/settle").principal(authentication))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/profiles/7/accounting/periods/2026-08/freeze")
                .principal(authentication)
                .contentType("application/json")
                .content("{\"reason\":\"Ready\"}"))
        .andExpect(status().isNoContent());

    verify(accounting).settle(7L, YearMonth.of(2026, 8));
    verify(accounting).freeze(7L, YearMonth.of(2026, 8), "owner", "Ready");
  }
}
