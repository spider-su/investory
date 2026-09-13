package com.smartbox.investory.accounting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingInvoiceIngestionService;
import com.smartbox.investory.accounting.AccountingPocRepository;
import com.smartbox.investory.accounting.AccountingSourceEvidenceService;
import com.smartbox.investory.accounting.AccountingSourceRepository;
import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.accounting.AccountingSourceType;
import com.smartbox.investory.accounting.api.AccountingUserApi.ReviewedDocument;
import com.smartbox.investory.accounting.staging.AccountingStagingAcquisitionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountingUserFacadeReviewedDocumentTest {
  private final AccountingPocRepository repository =
      org.mockito.Mockito.mock(AccountingPocRepository.class);
  private final AccountingSourceEvidenceService sources =
      org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
  private final AccountingStagingAcquisitionService staging =
      org.mockito.Mockito.mock(AccountingStagingAcquisitionService.class);
  private final AccountingUserFacade facade =
      new AccountingUserFacade(
          org.mockito.Mockito.mock(com.smartbox.investory.accounting.AccountingFactService.class),
          org.mockito.Mockito.mock(com.smartbox.investory.accounting.AccountingFilingService.class),
          repository,
          sources,
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.AccountingInvoiceRecognitionService.class),
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.AccountingDocumentExtractionService.class),
          staging,
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService
                  .class),
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.staging.AccountingBankStagingImportService.class),
          Optional.empty());

  @Test
  void mapsReviewedDueDateThroughFacadeWithoutLosingCountry() {
    when(repository.profileExists(1L)).thenReturn(true);
    when(sources.findId(1L, AccountingSourceType.UPLOAD, "upload-1")).thenReturn(Optional.of(42L));
    when(sources.findSource(1L, AccountingSourceType.UPLOAD, "upload-1"))
        .thenReturn(Optional.of(source(42L, "upload-1")));
    var dueDate = LocalDate.of(2026, 7, 31);
    var document =
        new ReviewedDocument(
            "upload-1",
            "SALES_INVOICE",
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 10),
            dueDate,
            "INV-1",
            "Customer",
            "DE123",
            "DE",
            "BUSINESS_SERVICE",
            "EUR",
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            null,
            "EU_B2B_REVERSE_CHARGE",
            "reviewed",
            YearMonth.of(2026, 7));

    facade.saveReviewed(1L, document);

    var captor = ArgumentCaptor.forClass(AccountingInvoiceIngestionService.ReviewedInvoice.class);
    org.mockito.Mockito.verify(staging)
        .stageInvoice(
            org.mockito.ArgumentMatchers.eq(1L),
            captor.capture(),
            org.mockito.ArgumentMatchers.eq("EU_B2B_REVERSE_CHARGE"));
    assertThat(captor.getValue().dueDate()).isEqualTo(dueDate);
    assertThat(captor.getValue().counterpartyCountry()).isEqualTo("DE");
  }

  @Test
  void acceptsLegacyDomesticVatValueForPurchaseDocuments() {
    when(repository.profileExists(1L)).thenReturn(true);
    when(sources.findId(1L, AccountingSourceType.UPLOAD, "upload-purchase"))
        .thenReturn(Optional.of(43L));
    when(sources.findSource(1L, AccountingSourceType.UPLOAD, "upload-purchase"))
        .thenReturn(Optional.of(source(43L, "upload-purchase")));
    var document =
        new ReviewedDocument(
            "upload-purchase",
            "PURCHASE_INVOICE",
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 2),
            null,
            "I26394B03000087",
            "BP Europa SE",
            "9720865431",
            "PL",
            "VEHICLE_FUEL",
            "PLN",
            new BigDecimal("253.34"),
            new BigDecimal("58.27"),
            new BigDecimal("311.61"),
            new BigDecimal("1.00"),
            "DOMESTIC_VAT",
            "fuel",
            YearMonth.of(2026, 1));

    facade.saveReviewed(1L, document);

    org.mockito.Mockito.verify(staging)
        .stageInvoice(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("DOMESTIC_PURCHASE"));
  }

  private static AccountingSourceRepository.SourceRow source(long id, String reference) {
    return new AccountingSourceRepository.SourceRow(
        id,
        AccountingSourceType.UPLOAD,
        reference,
        "invoice.pdf",
        "application/pdf",
        null,
        AccountingSourceStatus.PARSED,
        null,
        new byte[] {1});
  }
}
