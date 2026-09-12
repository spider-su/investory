package com.smartbox.investory.accounting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
  private final AccountingSourceEvidenceService sourceEvidenceService =
      org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new AccountingFactController(
                    service,
                    recognitionService,
                    invoiceIngestionService,
                    exporter,
                    sourceEvidenceService))
            .build();
    when(service.availablePeriods()).thenReturn(List.of(JANUARY, FEBRUARY, JULY));
    when(service.facts()).thenReturn(List.of());
    when(service.snapshot(any(LocalDate.class)))
        .thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
  }

  @Test
  void persistsUploadBeforeRecognition() throws Exception {
    byte[] payload = "pdf".getBytes();
    when(sourceEvidenceService.receiveUpload("invoice.pdf", "application/pdf", payload))
        .thenReturn(42L);
    when(recognitionService.recognize("invoice.pdf", "application/pdf", payload))
        .thenReturn(
            new AccountingInvoiceRecognitionService.RecognizedInvoice(
                "PURCHASE_INVOICE",
                JULY,
                JULY,
                null,
                "EXP-42",
                "Seller",
                null,
                "ACCOUNTING_SERVICE",
                "PLN",
                new BigDecimal("100"),
                new BigDecimal("23"),
                new BigDecimal("123"),
                null));

    mvc.perform(
            multipart("/poc/accounting/invoice/recognize")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "invoice", "invoice.pdf", "application/pdf", payload))
                .param("month", "2026-07"))
        .andExpect(status().isOk());

    var order = org.mockito.Mockito.inOrder(sourceEvidenceService, recognitionService);
    order.verify(sourceEvidenceService).receiveUpload("invoice.pdf", "application/pdf", payload);
    order.verify(recognitionService).recognize("invoice.pdf", "application/pdf", payload);
    order.verify(sourceEvidenceService).status(42L, AccountingSourceStatus.PARSED, null);
  }

  @Test
  void keepsUploadEvidenceWhenRecognitionFails() throws Exception {
    byte[] payload = "bad".getBytes();
    when(sourceEvidenceService.receiveUpload("bad.pdf", "application/pdf", payload))
        .thenReturn(43L);
    when(recognitionService.recognize("bad.pdf", "application/pdf", payload))
        .thenThrow(new IllegalStateException("recognition failed"));

    mvc.perform(
            multipart("/poc/accounting/invoice/recognize")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "invoice", "bad.pdf", "application/pdf", payload))
                .param("month", "2026-07"))
        .andExpect(status().isOk());

    verify(sourceEvidenceService).status(43L, AccountingSourceStatus.FAILED, "recognition failed");
  }

  @Test
  void reviewedUploadIsPersistedThroughAccountingIngestionService() throws Exception {
    when(invoiceIngestionService.ingest(any())).thenReturn(true);

    mvc.perform(
            post("/poc/accounting/invoice")
                .param("month", "2026-07")
                .param("sourceIdentity", "42")
                .param("documentType", "PURCHASE_INVOICE")
                .param("issueDate", "2026-07-10")
                .param("reference", "REVIEWED-42")
                .param("counterpartyAlias", "Supplier")
                .param("category", "ACCOUNTING_SERVICE")
                .param("currency", "PLN")
                .param("netAmount", "100")
                .param("vatAmount", "23")
                .param("grossAmount", "123")
                .param("vatDeductionRatio", "1"))
        .andExpect(status().is3xxRedirection());

    verify(invoiceIngestionService).ingest(any());
    verify(sourceEvidenceService).status(42L, AccountingSourceStatus.IMPORTED, null);
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
