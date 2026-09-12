package com.smartbox.investory.accounting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountingFactControllerTest {
  private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);
  private static final LocalDate FEBRUARY = LocalDate.of(2026, 2, 1);
  private static final LocalDate JULY = LocalDate.of(2026, 7, 1);

  private final AccountingFactService service =
      org.mockito.Mockito.mock(AccountingFactService.class);
  private final AccountingInvoiceRecognitionService recognitionService =
      org.mockito.Mockito.mock(AccountingInvoiceRecognitionService.class);
  private final AccountingInvoiceIngestionService invoiceIngestionService =
      org.mockito.Mockito.mock(AccountingInvoiceIngestionService.class);
  private final AccountingJdgExporter exporter =
      org.mockito.Mockito.mock(AccountingJdgExporter.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new AccountingFactController(
                    service, recognitionService, invoiceIngestionService, exporter))
            .build();
    when(service.availablePeriods()).thenReturn(List.of(JANUARY, FEBRUARY, JULY));
    when(service.facts()).thenReturn(List.of());
    when(service.snapshot(any(LocalDate.class)))
        .thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
  }

  @Test
  void selectsRequestedMonthAndExposesAdjacentPeriods() throws Exception {
    mvc.perform(get("/poc/accounting").param("month", "2026-02"))
        .andExpect(status().isOk())
        .andExpect(view().name("poc/accounting-facts"))
        .andExpect(model().attribute("selectedPeriod", FEBRUARY))
        .andExpect(model().attribute("previousPeriod", JANUARY))
        .andExpect(model().attribute("nextPeriod", JULY));
  }

  @Test
  void profileToggleKeepsSelectedMonthAndUpdatesSharedAssumption() throws Exception {
    mvc.perform(post("/poc/accounting/profile").param("month", "2026-02").param("hasUop", "false"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                "/poc/accounting?month=2026-02"));

    verify(service).updateHasUop(false);
  }

  private AccountingMonthSnapshot snapshot(LocalDate period) {
    AccountingMonthSnapshot.ZusCalculation zus =
        new AccountingMonthSnapshot.ZusCalculation(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, "fixture");
    AccountingMonthSnapshot.FxCalculation fx =
        new AccountingMonthSnapshot.FxCalculation(
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "NO_FX_SOURCE");
    AccountingMonthSnapshot.RyczaltCalculation ryczalt =
        new AccountingMonthSnapshot.RyczaltCalculation(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.12"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "NO_GOLDEN");
    AccountingMonthSnapshot.VatCalculation vat =
        new AccountingMonthSnapshot.VatCalculation(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "NO_GOLDEN");
    return new AccountingMonthSnapshot(
        period,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        fx,
        ryczalt,
        vat,
        zus,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
