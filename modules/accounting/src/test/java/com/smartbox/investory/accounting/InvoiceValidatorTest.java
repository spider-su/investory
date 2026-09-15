package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceValidatorTest {
  private final InvoiceValidator validator = new InvoiceValidator();

  @Test
  void acceptsConsistentInvoiceAndResolvesSalesDirection() {
    AccountingDocumentCandidate candidate = candidate("1234567890", "9999999999", "SALES_INVOICE");

    assertThat(validator.validate(validator.resolveDirection(candidate, "123 456 78 90")))
        .isEmpty();
    assertThat(validator.resolveDirection(candidate, "123 456 78 90").documentType())
        .isEqualTo("SALES_INVOICE");
  }

  @Test
  void resolvesPurchaseAndRequiresReviewWhenDirectionIsUnknown() {
    AccountingDocumentCandidate candidate = candidate("1234567890", "9999999999", "UNKNOWN");

    assertThat(validator.resolveDirection(candidate, "9999999999").documentType())
        .isEqualTo("PURCHASE_INVOICE");
    assertThat(validator.validate(validator.resolveDirection(candidate, "1111111111")))
        .contains("invoice direction requires review");
  }

  @Test
  void rejectsArithmeticMismatch() {
    AccountingDocumentCandidate candidate =
        new AccountingDocumentCandidate(
            "SALES_INVOICE",
            "FV/1",
            LocalDate.of(2026, 1, 1),
            null,
            null,
            "Seller",
            "1234567890",
            "Buyer",
            "9999999999",
            "PLN",
            new BigDecimal("100"),
            new BigDecimal("23"),
            new BigDecimal("130"),
            List.of(),
            "OTHER",
            List.of(),
            "test-v1");

    assertThat(validator.validate(candidate)).contains("net + VAT does not equal gross");
  }

  private AccountingDocumentCandidate candidate(String sellerNip, String buyerNip, String type) {
    return new AccountingDocumentCandidate(
        type,
        "FV/1",
        LocalDate.of(2026, 1, 1),
        null,
        null,
        "Seller",
        sellerNip,
        "Buyer",
        buyerNip,
        "PLN",
        new BigDecimal("100"),
        new BigDecimal("23"),
        new BigDecimal("123"),
        List.of(),
        "OTHER",
        List.of(),
        "test-v1");
  }
}
