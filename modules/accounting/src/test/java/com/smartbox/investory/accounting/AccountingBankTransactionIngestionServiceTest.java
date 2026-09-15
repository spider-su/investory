package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.accounting.service.AccountingBankTransactionIngestionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AccountingBankTransactionIngestionServiceTest {
  private final AccountingPocRepository repository = mock(AccountingPocRepository.class);
  private final AccountingBankTransactionIngestionService service =
      new AccountingBankTransactionIngestionService(repository);

  @Test
  void classifiesReceiptAndUsesStableSourceRowIdentity() {
    var result = service.ingest(row("CUSTOMER RECEIPT", "CUSTOMER A", "123.00"), 4L, "4:1");

    assertThat(result)
        .extracting(
            AccountingBankTransactionIngestionService.Result::reviewRequired,
            AccountingBankTransactionIngestionService.Result::transactionType)
        .containsExactly(false, "CUSTOMER_RECEIPT");
    verify(repository)
        .insertBankTransaction(
            eq(LocalDate.of(2026, 9, 15)),
            eq(LocalDate.of(2026, 9, 1)),
            eq("CUSTOMER RECEIPT"),
            eq("CUSTOMER A"),
            eq("PLN"),
            eq(new BigDecimal("123.00")),
            eq("CUSTOMER_RECEIPT"),
            eq("BUSINESS"),
            org.mockito.ArgumentMatchers.contains("Bank source row 4:1"),
            eq(4L),
            eq("4:1"));
  }

  @Test
  void excludesInternalTransferAndReviewsUnknownPayment() {
    var internal = service.ingest(row("TRANSFER", "OWN_ACCOUNT", "-50.00"), 4L, "4:2");
    var unknown = service.ingest(row("CARD CHARGE", "UNKNOWN", "-50.00"), 4L, "4:3");

    assertThat(internal)
        .extracting(
            AccountingBankTransactionIngestionService.Result::reviewRequired,
            AccountingBankTransactionIngestionService.Result::transactionType)
        .containsExactly(false, "INTERNAL_TRANSFER");
    assertThat(unknown)
        .extracting(
            AccountingBankTransactionIngestionService.Result::reviewRequired,
            AccountingBankTransactionIngestionService.Result::transactionType)
        .containsExactly(true, "UNKNOWN");
  }

  @Test
  void classifiesUnlabelledPositiveBusinessTransferAsCustomerReceipt() {
    var result =
        service.ingest(row("BANK-REF-1", "IT PLATFORM SOLUTIONS LIMITED", "7636.00"), 4L, "4:8");

    assertThat(result)
        .extracting(
            AccountingBankTransactionIngestionService.Result::reviewRequired,
            AccountingBankTransactionIngestionService.Result::transactionType)
        .containsExactly(false, "CUSTOMER_RECEIPT");
  }

  @Test
  void classifiesGovernmentAndSupplierPaymentsByExplicitReference() {
    assertThat(service.ingest(row("VAT-7", "TAX_OFFICE", "-20.00"), 4L, "4:4").transactionType())
        .isEqualTo("VAT_PAYMENT");
    assertThat(
            service.ingest(row("26M09 PPE", "TAX_OFFICE", "-30.00"), 4L, "4:5").transactionType())
        .isEqualTo("RYCZALT_PAYMENT");
    assertThat(service.ingest(row("ZUS 09/2026", "ZUS", "-40.00"), 4L, "4:6").transactionType())
        .isEqualTo("ZUS_PAYMENT");
    assertThat(
            service.ingest(row("SUPPLIER-42", "SUPPLIER", "-50.00"), 4L, "4:7").transactionType())
        .isEqualTo("SUPPLIER_PAYMENT");
  }

  @Test
  void excludesPersonalPaymentFromBusinessBankTotals() {
    var result = service.ingest(row("PERSONAL PAYMENT", "PERSONAL", "-740.00"), 4L, "4:9");

    assertThat(result)
        .extracting(
            AccountingBankTransactionIngestionService.Result::reviewRequired,
            AccountingBankTransactionIngestionService.Result::transactionType)
        .containsExactly(false, "PRIVATE_PAYMENT");
    verify(repository)
        .insertBankTransaction(
            eq(LocalDate.of(2026, 9, 15)),
            eq(LocalDate.of(2026, 9, 1)),
            eq("PERSONAL PAYMENT"),
            eq("PERSONAL"),
            eq("PLN"),
            eq(new BigDecimal("-740.00")),
            eq("PRIVATE_PAYMENT"),
            eq("EXCLUDED_PRIVATE"),
            org.mockito.ArgumentMatchers.contains("Bank source row 4:9"),
            eq(4L),
            eq("4:9"));
  }

  @Test
  void excludesPrivateRentalTaxDespitePpeRyczaltAbbreviation() {
    var result = service.ingest(row("26M07 PPE - rental", "TAX_OFFICE", "-740.00"), 4L, "4:10");

    assertThat(result)
        .extracting(
            AccountingBankTransactionIngestionService.Result::reviewRequired,
            AccountingBankTransactionIngestionService.Result::transactionType)
        .containsExactly(false, "PRIVATE_PAYMENT");
    verify(repository)
        .insertBankTransaction(
            eq(LocalDate.of(2026, 9, 15)),
            eq(LocalDate.of(2026, 9, 1)),
            eq("26M07 PPE - rental"),
            eq("TAX_OFFICE"),
            eq("PLN"),
            eq(new BigDecimal("-740.00")),
            eq("PRIVATE_PAYMENT"),
            eq("EXCLUDED_PRIVATE"),
            org.mockito.ArgumentMatchers.contains("Bank source row 4:10"),
            eq(4L),
            eq("4:10"));
  }

  private AccountingBankFileParser.ParsedBankTransaction row(
      String reference, String counterparty, String amount) {
    return new AccountingBankFileParser.ParsedBankTransaction(
        LocalDate.of(2026, 9, 15),
        LocalDate.of(2026, 9, 1),
        reference,
        counterparty,
        "PLN",
        new BigDecimal(amount),
        "note");
  }
}
