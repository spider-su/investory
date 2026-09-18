package com.smartbox.investory.accounting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.accounting.AccountingSourceType;
import com.smartbox.investory.accounting.api.AccountingUserApi.ReviewedDocument;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingSourceRepository;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import com.smartbox.investory.accounting.staging.AccountingStagingAcquisitionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountingUserFacadeReviewedDocumentTest {
  private final AccountingPocRepository repository =
      org.mockito.Mockito.mock(AccountingPocRepository.class);
  private final AccountingSourceEvidenceService sources =
      org.mockito.Mockito.mock(AccountingSourceEvidenceService.class);
  private final AccountingStagingAcquisitionService staging =
      org.mockito.Mockito.mock(AccountingStagingAcquisitionService.class);
  private final com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService
      stagingReconciliation =
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService
                  .class);
  private final com.smartbox.investory.accounting.staging.AccountingBankStagingImportService
      bankImport =
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.staging.AccountingBankStagingImportService.class);
  private final AccountingUserFacade facade =
      new AccountingUserFacade(
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.service.AccountingFactService.class),
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.service.AccountingFilingService.class),
          repository,
          sources,
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.service.AccountingInvoiceRecognitionService.class),
          org.mockito.Mockito.mock(
              com.smartbox.investory.accounting.service.AccountingDocumentExtractionService.class),
          staging,
          stagingReconciliation,
          bankImport,
          Optional.empty());

  @Test
  void serializesRecognitionMoneyAsDecimalStrings() throws Exception {
    var candidate =
        new com.smartbox.investory.accounting.api.AccountingUserApi.CandidateView(
            "upload-1",
            "PURCHASE_INVOICE",
            null,
            null,
            null,
            "FV-1",
            "Supplier",
            null,
            null,
            null,
            null,
            "PLN",
            new BigDecimal("123.45"),
            new BigDecimal("23.45"),
            new BigDecimal("146.90"),
            null,
            "PARSED",
            false,
            null,
            null,
            new BigDecimal("23"),
            List.of(),
            List.of());

    var json = new ObjectMapper().writeValueAsString(candidate);

    assertThat(json).contains("\"netAmount\":\"123.45\"");
    assertThat(json).contains("\"vatRate\":\"23\"");
  }

  @Test
  void exposesBackendOwnedVatRequiredInputsAndConditionalFields() {
    var inputs = AccountingUserFacade.requiredInputs("PURCHASE_INVOICE");

    assertThat(inputs)
        .extracting(input -> input.field())
        .containsExactly("vatTreatment", "vatRate", "counterpartyCountry");
    assertThat(inputs.getFirst().required()).isTrue();
    assertThat(inputs.getFirst().options())
        .extracting(option -> option.value())
        .containsExactly("DOMESTIC_PURCHASE", "IMPORT_OF_SERVICES_EU", "IMPORT_OF_SERVICES_NON_EU");
    assertThat(inputs.get(1).dependsOn()).isEqualTo("vatTreatment");
    assertThat(inputs.get(1).dependsOnValues()).containsExactly("DOMESTIC_PURCHASE");
    assertThat(inputs.get(2).dependsOnValues())
        .containsExactly("IMPORT_OF_SERVICES_EU", "IMPORT_OF_SERVICES_NON_EU");
  }

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
    org.mockito.Mockito.verify(stagingReconciliation).reconcile(1L, YearMonth.of(2026, 7).atDay(1));
  }

  @Test
  void exposesPersistedPaymentHistoryWithoutRecalculatingObligations() {
    when(repository.profileExists(1L)).thenReturn(true);
    when(repository.obligationsForPeriod(1L, LocalDate.of(2026, 7, 1)))
        .thenReturn(
            java.util.List.of(
                new ObligationRow(
                    LocalDate.of(2026, 7, 1),
                    "VAT",
                    LocalDate.of(2026, 8, 25),
                    new BigDecimal("1230.00"),
                    new BigDecimal("500.00"),
                    LocalDate.of(2026, 8, 20),
                    "PARTIALLY_PAID",
                    null)));

    var history = facade.paymentHistory(1L, YearMonth.of(2026, 7), YearMonth.of(2026, 7), "vat");

    assertThat(history).hasSize(1);
    assertThat(history.getFirst().type()).isEqualTo("VAT");
    assertThat(history.getFirst().period()).isEqualTo(YearMonth.of(2026, 7));
    assertThat(history.getFirst().amount()).isEqualByComparingTo("1230.00");
    assertThat(history.getFirst().paidAmount()).isEqualByComparingTo("500.00");
    assertThat(history.getFirst().outstandingAmount()).isEqualByComparingTo("730.00");
    assertThat(history.getFirst().paymentDate()).isEqualTo(LocalDate.of(2026, 8, 20));
  }

  @Test
  void rejectsUnknownPaymentHistoryType() {
    when(repository.profileExists(1L)).thenReturn(true);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> facade.paymentHistory(1L, YearMonth.of(2026, 7), YearMonth.of(2026, 7), "CIT"))
        .withMessage("Unsupported obligation type");
  }

  @Test
  void doesNotTreatUnknownDocumentTypeAsSalesDocument() {
    when(repository.profileExists(1L)).thenReturn(true);

    var document =
        new ReviewedDocument(
            "upload-unknown",
            "UNRECOGNIZED",
            null,
            null,
            null,
            "UNKNOWN-1",
            null,
            null,
            null,
            null,
            "PLN",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> facade.saveReviewedResult(1L, document))
        .withMessage("Unsupported document type");
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
            YearMonth.of(2026, 1),
            new BigDecimal("23.00"));

    facade.saveReviewed(1L, document);

    org.mockito.Mockito.verify(staging)
        .stageInvoice(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("DOMESTIC_PURCHASE"));
  }

  @Test
  void booksCreditNoteInItsIssueMonthByDefault() {
    when(repository.profileExists(1L)).thenReturn(true);
    when(sources.findId(1L, AccountingSourceType.UPLOAD, "upload-credit"))
        .thenReturn(Optional.of(45L));
    when(sources.findSource(1L, AccountingSourceType.UPLOAD, "upload-credit"))
        .thenReturn(Optional.of(source(45L, "upload-credit")));
    var document =
        new ReviewedDocument(
            "upload-credit",
            "CREDIT_NOTE",
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 10),
            null,
            "CN-1",
            "Customer",
            "PL123",
            "PL",
            "BUSINESS_SERVICE",
            "PLN",
            new BigDecimal("100.00"),
            new BigDecimal("23.00"),
            new BigDecimal("123.00"),
            null,
            "DOMESTIC_VAT",
            "correction",
            YearMonth.of(2026, 6),
            new BigDecimal("23"));

    facade.saveReviewed(1L, document);

    var captor = ArgumentCaptor.forClass(AccountingInvoiceIngestionService.ReviewedInvoice.class);
    org.mockito.Mockito.verify(staging)
        .stageInvoice(
            org.mockito.ArgumentMatchers.eq(1L),
            captor.capture(),
            org.mockito.ArgumentMatchers.eq("DOMESTIC_VAT"));
    assertThat(captor.getValue().taxPeriod()).isEqualTo(LocalDate.of(2026, 7, 1));
    org.mockito.Mockito.verify(stagingReconciliation).reconcile(1L, LocalDate.of(2026, 7, 1));
  }

  @Test
  void keepsReviewerCountryForKsefEvidence() {
    when(repository.profileExists(1L)).thenReturn(true);
    when(sources.findSource(1L, AccountingSourceType.KSEF, "ksef-1"))
        .thenReturn(Optional.of(ksefSource(44L, "ksef-1")));
    var document =
        new ReviewedDocument(
            "ksef-1",
            "SALES_INVOICE",
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 10),
            null,
            "KSEF-1",
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
    assertThat(captor.getValue().counterpartyCountry()).isEqualTo("DE");
  }

  @Test
  void reconcilesEveryAccountingMonthTouchedByBankImport() {
    when(repository.profileExists(1L)).thenReturn(true);
    when(bankImport.stageFile(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new com.smartbox.investory.accounting.staging.AccountingBankStagingImportService.Result(
                1L, 2, 2, Set.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1))));

    facade.importBank(1L, "bank.csv", "text/csv", new byte[] {1}, YearMonth.of(2026, 7));

    org.mockito.Mockito.verify(stagingReconciliation).reconcile(1L, LocalDate.of(2026, 7, 1));
    org.mockito.Mockito.verify(stagingReconciliation).reconcile(1L, LocalDate.of(2026, 8, 1));
  }

  @Test
  void rejectsDomesticReviewWithoutVatRateBeforeItCanBlockFiling() {
    when(repository.profileExists(1L)).thenReturn(true);
    var document =
        new ReviewedDocument(
            "upload-1",
            "SALES_INVOICE",
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 10),
            null,
            "INV-1",
            "Customer",
            "PL123",
            "PL",
            "BUSINESS_SERVICE",
            "PLN",
            new BigDecimal("100.00"),
            new BigDecimal("23.00"),
            new BigDecimal("123.00"),
            null,
            "DOMESTIC_VAT",
            "reviewed",
            YearMonth.of(2026, 7));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> facade.saveReviewed(1L, document))
        .withMessage("VAT rate is required for domestic VAT treatment");
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

  private static AccountingSourceRepository.SourceRow ksefSource(long id, String reference) {
    return new AccountingSourceRepository.SourceRow(
        id,
        AccountingSourceType.KSEF,
        reference,
        "invoice.xml",
        "application/xml",
        LocalDate.of(2026, 7, 10),
        AccountingSourceStatus.REVIEW_REQUIRED,
        null,
        new byte[] {1});
  }
}
