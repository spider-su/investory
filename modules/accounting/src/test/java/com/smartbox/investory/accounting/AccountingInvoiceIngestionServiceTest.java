package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
