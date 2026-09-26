package com.smartbox.investory.ryczalt.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.config.RestApiExceptionHandler;
import com.smartbox.investory.ryczalt.application.RyczaltAccountingApi;
import com.smartbox.investory.ryczalt.application.RyczaltInvoicePaymentService;
import com.smartbox.investory.ryczalt.application.RyczaltJpkService;
import com.smartbox.investory.ryczalt.application.RyczaltZusDraService;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodNotFoundException;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationResult;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.InvoicePaymentStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.ryczalt.pit28.Pit28AnnualFacts;
import com.smartbox.investory.ryczalt.pit28.Pit28Calculation;
import com.smartbox.investory.ryczalt.pit28.Pit28Draft;
import com.smartbox.investory.ryczalt.pit28.Pit28PreviewService;
import com.smartbox.investory.ryczalt.pit28.Pit28Readiness;
import com.smartbox.investory.ryczalt.pit28.Pit28ReadinessStatus;
import com.smartbox.investory.ryczalt.reference.RyczaltObligationReferenceReader;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RyczaltAccountingRestControllerTest {
  private final RyczaltAccountingApi accounting = mock(RyczaltAccountingApi.class);
  private final RyczaltInvoicePaymentService invoicePayments =
      mock(RyczaltInvoicePaymentService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final RyczaltObligationReferenceReader referenceObligations =
      mock(RyczaltObligationReferenceReader.class);
  private final RyczaltJpkService jpk = mock(RyczaltJpkService.class);
  private final RyczaltZusDraService zusDra = mock(RyczaltZusDraService.class);
  private final Pit28PreviewService pit28 = mock(Pit28PreviewService.class);
  private final Authentication authentication = mock(Authentication.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    when(authentication.getName()).thenReturn("owner");
    when(authorization.canRead(7L, authentication)).thenReturn(true);
    when(authorization.canWrite(7L, authentication)).thenReturn(true);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new RyczaltAccountingRestController(
                    accounting,
                    invoicePayments,
                    authorization,
                    referenceObligations,
                    jpk,
                    zusDra,
                    pit28))
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
  void referenceObligationsUsePersistedReferenceSource() throws Exception {
    when(referenceObligations.find(7L, YearMonth.of(2026, 8)))
        .thenReturn(
            List.of(
                new RyczaltObligationReferenceReader.ReferenceObligation(
                    "RYCZALT", new BigDecimal("5809.00"))));

    mvc.perform(
            get("/api/profiles/7/accounting/periods/2026-08/reference-obligations")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(content().json("[{\"type\":\"RYCZALT\",\"expected\":5809.00}]"));

    verify(referenceObligations).find(7L, YearMonth.of(2026, 8));
  }

  @Test
  void jpkIsReturnedAsAnAttachmentFromTheNativeRoute() throws Exception {
    when(jpk.generate(7L, YearMonth.of(2026, 8)))
        .thenReturn(
            new RyczaltJpkService.Document(
                "JPK_V7M_7_2026-08.xml",
                "<JPK/>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    mvc.perform(get("/api/profiles/7/accounting/periods/2026-08/jpk").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/xml"))
        .andExpect(content().string("<JPK/>"));

    verify(jpk).generate(7L, YearMonth.of(2026, 8));
  }

  @Test
  void zusDraIsReturnedAsAnAttachmentFromTheNativeRoute() throws Exception {
    when(zusDra.generate(7L, YearMonth.of(2026, 8)))
        .thenReturn(
            new RyczaltZusDraService.Document(
                "ZUS_DRA_7_2026-08.xml",
                "<KEDU/>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    mvc.perform(get("/api/profiles/7/accounting/periods/2026-08/zus-dra").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/xml"))
        .andExpect(content().string("<KEDU/>"));

    verify(zusDra).generate(7L, YearMonth.of(2026, 8));
  }

  @Test
  void pit28PreviewExposesReadyAnnualValues() throws Exception {
    var facts =
        new Pit28AnnualFacts(
            2026,
            new BigDecimal("100000"),
            Map.of("PLN", new BigDecimal("100000")),
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            new BigDecimal("500"),
            new BigDecimal("250"),
            new BigDecimal("0.12"),
            new BigDecimal("10000"));
    var calculation =
        new Pit28Calculation(
            2026,
            new BigDecimal("100000"),
            new BigDecimal("1000"),
            new BigDecimal("250"),
            new BigDecimal("98750"),
            new BigDecimal("0.12"),
            new BigDecimal("11850"),
            new BigDecimal("10000"),
            new BigDecimal("1850"),
            BigDecimal.ZERO);
    when(pit28.preview(7L, 2026))
        .thenReturn(
            new Pit28Draft(
                2026,
                new Pit28Readiness(Pit28ReadinessStatus.READY, List.of()),
                facts,
                calculation,
                List.of()));

    mvc.perform(get("/api/profiles/7/accounting/pit28/2026").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json("{\"year\":2026,\"status\":\"READY\",\"tax\":{\"annualTax\":\"11850\"}}"));
  }

  @Test
  void calculateExposesNativeMonthCycleAsWriteCommand() throws Exception {
    when(accounting.calculateFromPersistedFacts(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(YearMonth.of(2026, 9))))
        .thenReturn(mock(NativeMonthCalculationResult.class));

    mvc.perform(
            post("/api/profiles/7/accounting/periods/2026-09/calculate")
                .principal(authentication)
                .contentType("application/json"))
        .andExpect(status().isOk());

    verify(accounting)
        .calculateFromPersistedFacts(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(YearMonth.of(2026, 9)));
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
  void invoiceCounterpartyIncludesTaxIdentifierWhenKnownAndNullWhenMissing() throws Exception {
    when(accounting.invoices(7L, YearMonth.of(2026, 8)))
        .thenReturn(
            List.of(
                invoice(
                    11,
                    new RyczaltInvoiceReadModel.CounterpartyView(
                        123L, "Example Sp. z o.o.", null, "1234567890")),
                invoice(
                    12,
                    new RyczaltInvoiceReadModel.CounterpartyView(
                        124L, "Unknown ID Sp. z o.o.", null, null))));

    mvc.perform(
            get("/api/profiles/7/accounting/periods/2026-08/invoices").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "[{\"id\":11,\"counterparty\":{\"id\":123,\"legalName\":\"Example Sp. z"
                        + " o.o.\",\"alias\":null,\"taxIdentifier\":\"1234567890\"}},{\"id\":12,\"counterparty\":{\"id\":124,\"legalName\":\"Unknown"
                        + " ID Sp. z o.o.\",\"alias\":null,\"taxIdentifier\":null}}]"));
  }

  private RyczaltInvoiceReadModel invoice(
      long id, RyczaltInvoiceReadModel.CounterpartyView counterparty) {
    return new RyczaltInvoiceReadModel(
        id,
        InvoiceDirection.INCOME,
        "FV/" + id,
        LocalDate.of(2026, 8, 20),
        LocalDate.of(2026, 8, 20),
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.TEN,
        CurrencyType.PLN,
        BigDecimal.TEN,
        new BigDecimal("0.12"),
        null,
        null,
        counterparty,
        ApprovalStatus.NEEDS_REVIEW,
        null,
        PaymentVerificationPolicy.REQUIRED,
        InvoicePaymentStatus.UNMATCHED,
        null,
        null);
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
  void manualPaidEndpointDelegatesDateAndNote() throws Exception {
    mvc.perform(
            post("/api/profiles/7/accounting/invoices/11/manual-paid")
                .principal(authentication)
                .contentType("application/json")
                .content("{\"paidDate\":\"2026-08-25\",\"note\":\"Paid in cash\"}"))
        .andExpect(status().isNoContent());

    verify(invoicePayments).markPaid(7L, 11L, LocalDate.of(2026, 8, 25), "Paid in cash");
  }

  @Test
  void manualUnpaidEndpointDelegatesInvoice() throws Exception {
    mvc.perform(
            delete("/api/profiles/7/accounting/invoices/11/manual-paid").principal(authentication))
        .andExpect(status().isNoContent());

    verify(invoicePayments).markUnpaid(7L, 11L);
  }

  @Test
  void manualPaidEndpointDelegatesObligationDateAndNote() throws Exception {
    mvc.perform(
            post("/api/profiles/7/accounting/periods/2026-08/obligations/21/manual-paid")
                .principal(authentication)
                .contentType("application/json")
                .content("{\"paidDate\":\"2026-08-25\",\"note\":\"Paid without transfer\"}"))
        .andExpect(status().isNoContent());

    verify(accounting)
        .markObligationPaid(7L, 21L, LocalDate.of(2026, 8, 25), "Paid without transfer");
  }

  @Test
  void manualUnpaidEndpointDelegatesObligation() throws Exception {
    mvc.perform(
            delete("/api/profiles/7/accounting/periods/2026-08/obligations/21/manual-paid")
                .principal(authentication))
        .andExpect(status().isNoContent());

    verify(accounting).markObligationUnpaid(7L, 21L);
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
  void freezeUsesNativeCommand() throws Exception {
    mvc.perform(
            post("/api/profiles/7/accounting/periods/2026-08/freeze")
                .principal(authentication)
                .contentType("application/json")
                .content("{\"reason\":\"Ready\"}"))
        .andExpect(status().isNoContent());

    verify(accounting).freeze(7L, YearMonth.of(2026, 8), "owner", "Ready");
  }
}
