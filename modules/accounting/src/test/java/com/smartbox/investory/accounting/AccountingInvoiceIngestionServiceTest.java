package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountingInvoiceIngestionServiceTest {
  private final AccountingPocRepository repository =
      org.mockito.Mockito.mock(AccountingPocRepository.class);
  private AccountingInvoiceIngestionService service;

  @BeforeEach
  void setUp() {
    service = new AccountingInvoiceIngestionService(new AccountingExpenseNormalizer(), repository);
  }

  @Test
  void normalizesPurchaseVatAtEachSupportedDocumentRatio() {
    when(repository.insertExpense(
            any(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            anyString(),
            anyString()))
        .thenReturn(true);

    for (String ratio : new String[] {"0.00", "0.50", "1.00"}) {
      assertThat(service.ingest(purchase(new BigDecimal(ratio)))).isTrue();
    }

    verify(repository, org.mockito.Mockito.times(3))
        .insertExpense(
            any(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            anyString(),
            anyString());
  }

  @Test
  void preservesRepositoryDeduplicationResult() {
    when(repository.insertExpense(
            any(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            anyString(),
            anyString()))
        .thenReturn(false);

    assertThat(service.ingest(purchase(BigDecimal.ONE))).isFalse();
  }

  @Test
  void rejectsUnsupportedVatRatioAndNonReconcilingAmounts() {
    assertThatThrownBy(() -> service.ingest(purchase(new BigDecimal("0.25"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("0%, 50% or 100%");
    assertThatThrownBy(
            () -> service.ingest(purchaseWithGross(BigDecimal.ONE, new BigDecimal("99.00"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Net + VAT must equal gross");
  }

  @Test
  void keepsExplicitFixedAssetsOutOfThePocUntilTheirJpkTreatmentIsImplemented() {
    var invoice =
        new AccountingInvoiceIngestionService.ReviewedInvoice(
            LocalDate.of(2026, 7, 1),
            "PURCHASE_INVOICE",
            LocalDate.of(2026, 7, 10),
            null,
            "FIXED-ASSET-1",
            "Supplier",
            "FIXED_ASSET",
            "PLN",
            new BigDecimal("100.00"),
            new BigDecimal("23.00"),
            new BigDecimal("123.00"),
            BigDecimal.ONE,
            "SOURCE_DOCUMENT",
            "source");

    assertThatThrownBy(() -> service.ingest(invoice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FIXED_ASSET_UNSUPPORTED");
  }

  @Test
  void routesSalesWithoutApplyingPurchaseVatDeduction() {
    when(repository.insertSalesInvoice(
            any(),
            any(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyString()))
        .thenReturn(true);

    assertThat(service.ingest(sales())).isTrue();
    verify(repository)
        .insertSalesInvoice(
            any(),
            any(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyString());
  }

  @Test
  void persistsCreditNoteAsSignedSalesAdjustment() {
    when(repository.insertSalesInvoice(
            any(),
            any(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyString()))
        .thenReturn(true);

    assertThat(service.ingest(creditNote())).isTrue();
    org.mockito.ArgumentCaptor<String> kind = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.ArgumentCaptor<BigDecimal> amounts =
        org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
    verify(repository)
        .insertSalesInvoice(
            any(),
            any(),
            any(),
            anyString(),
            anyString(),
            kind.capture(),
            anyString(),
            amounts.capture(),
            amounts.capture(),
            amounts.capture(),
            any(),
            any(),
            anyString());
    assertThat(kind.getValue()).isEqualTo("CREDIT_NOTE");
    assertThat(amounts.getAllValues())
        .extracting(BigDecimal::toPlainString)
        .containsExactly("-100.00", "-23.00", "-123.00");
  }

  @Test
  void carriesReviewedDueDateAndCounterpartyCountryToProfileScopedPersistence() {
    var invoice = salesWithDueDateAndCountry(LocalDate.of(2026, 7, 31), "DE");
    assertThat(service.ingest(1L, invoice)).isFalse();

    verify(repository)
        .insertSalesInvoice(
            1L,
            invoice.taxPeriod(),
            invoice.issueDate(),
            invoice.saleDate(),
            invoice.dueDate(),
            invoice.reference(),
            invoice.counterpartyAlias(),
            "EU_SERVICE",
            "EUR",
            invoice.netAmount(),
            invoice.vatAmount(),
            invoice.grossAmount(),
            null,
            new BigDecimal("0.12"),
            invoice.note(),
            42L,
            invoice.counterpartyTaxIdentifier(),
            invoice.counterpartyCountry(),
            null,
            null);
  }

  @Test
  void canonicalizesCounterpartyWhenTaxIdentifierMatchesKnownRegistry() {
    var invoice = salesWithDueDateAndCountry(LocalDate.of(2026, 7, 31), "DE");
    when(repository.knownCounterparty(1L, "DE123", "DE"))
        .thenReturn(new AccountingKnownCounterparty("DE123", "DE", "Known Customer"));

    service.ingest(1L, invoice);

    verify(repository)
        .insertSalesInvoice(
            eq(1L),
            eq(invoice.taxPeriod()),
            eq(invoice.issueDate()),
            eq(invoice.saleDate()),
            eq(invoice.dueDate()),
            eq(invoice.reference()),
            eq("Known Customer"),
            eq("EU_SERVICE"),
            eq("EUR"),
            eq(invoice.netAmount()),
            eq(invoice.vatAmount()),
            eq(invoice.grossAmount()),
            eq(null),
            eq(new BigDecimal("0.12")),
            eq(invoice.note()),
            eq(42L),
            eq("DE123"),
            eq("DE"),
            eq(null),
            eq(null));
  }

  @Test
  void carriesOptionalReviewedDueDateAsNull() {
    assertThat(service.ingest(1L, sales())).isFalse();
    verify(repository)
        .insertSalesInvoice(
            any(Long.class),
            any(),
            any(),
            any(),
            org.mockito.ArgumentMatchers.isNull(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any());
  }

  @Test
  void dualWritesReviewedSalesIntoCanonicalDocumentAndVatBucket() {
    var invoice = salesWithDueDateAndCountry(LocalDate.of(2026, 7, 31), "DE");

    service.ingest(1L, invoice);

    verify(repository)
        .upsertCanonicalDocument(
            eq(1L),
            eq("SALE"),
            eq("INVOICE"),
            eq(invoice.taxPeriod()),
            eq(invoice.issueDate()),
            eq(invoice.saleDate()),
            eq(invoice.dueDate()),
            eq(invoice.reference()),
            eq(invoice.counterpartyAlias()),
            eq(invoice.counterpartyTaxIdentifier()),
            eq(invoice.counterpartyCountry()),
            eq("EUR"),
            eq(invoice.netAmount()),
            eq(invoice.vatAmount()),
            eq(invoice.grossAmount()),
            eq(null),
            eq(null),
            eq(new BigDecimal("0.12")),
            eq(null),
            eq(null),
            eq(null),
            eq(42L),
            eq(null),
            eq(null),
            eq(invoice.note()),
            eq(VatTreatment.EU_B2B_REVERSE_CHARGE),
            eq(null),
            eq(BigDecimal.ZERO));
  }

  private AccountingInvoiceIngestionService.ReviewedInvoice salesWithDueDateAndCountry(
      LocalDate dueDate, String country) {
    return new AccountingInvoiceIngestionService.ReviewedInvoice(
        LocalDate.of(2026, 7, 1),
        "SALES_INVOICE",
        LocalDate.of(2026, 7, 10),
        LocalDate.of(2026, 7, 10),
        "SALE-EU-1",
        "Customer",
        "BUSINESS_SERVICE",
        "EUR",
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        new BigDecimal("100.00"),
        null,
        "REVIEWED",
        "reviewed",
        "42",
        "DE123",
        country,
        null,
        null,
        dueDate);
  }

  private AccountingInvoiceIngestionService.ReviewedInvoice purchase(BigDecimal ratio) {
    return purchaseWithGross(ratio, new BigDecimal("123.00"));
  }

  private AccountingInvoiceIngestionService.ReviewedInvoice purchaseWithGross(
      BigDecimal ratio, BigDecimal gross) {
    return new AccountingInvoiceIngestionService.ReviewedInvoice(
        LocalDate.of(2026, 7, 1),
        "PURCHASE_INVOICE",
        LocalDate.of(2026, 7, 10),
        null,
        "SUP-1",
        "Supplier",
        "OTHER",
        "PLN",
        new BigDecimal("100.00"),
        new BigDecimal("23.00"),
        gross,
        ratio,
        "SOURCE_DOCUMENT",
        "source");
  }

  private AccountingInvoiceIngestionService.ReviewedInvoice sales() {
    return new AccountingInvoiceIngestionService.ReviewedInvoice(
        LocalDate.of(2026, 7, 1),
        "SALES_INVOICE",
        LocalDate.of(2026, 7, 10),
        LocalDate.of(2026, 7, 10),
        "SALE-1",
        "Customer",
        "BUSINESS_SERVICE",
        "EUR",
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        new BigDecimal("100.00"),
        null,
        "SOURCE_DOCUMENT",
        "source");
  }

  private AccountingInvoiceIngestionService.ReviewedInvoice creditNote() {
    return new AccountingInvoiceIngestionService.ReviewedInvoice(
        LocalDate.of(2026, 7, 1),
        "CREDIT_NOTE",
        LocalDate.of(2026, 7, 12),
        LocalDate.of(2026, 7, 12),
        "CN-1",
        "Customer",
        "BUSINESS_SERVICE",
        "PLN",
        new BigDecimal("100.00"),
        new BigDecimal("23.00"),
        new BigDecimal("123.00"),
        null,
        "SOURCE_DOCUMENT",
        "correction");
  }
}
