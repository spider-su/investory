package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Neutral source claim. It is not an accounting fact and must be validated before ingestion. */
public record AccountingDocumentCandidate(
    String documentType,
    String reference,
    LocalDate issueDate,
    LocalDate saleDate,
    LocalDate dueDate,
    String seller,
    String sellerNip,
    String buyer,
    String buyerNip,
    String currency,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal grossAmount,
    List<VatSummaryRow> vatSummaryRows,
    String suggestedCategory,
    List<FieldCandidate<?>> fields,
    String parserVersion) {
  public record VatSummaryRow(BigDecimal rate, BigDecimal net, BigDecimal vat, BigDecimal gross) {}
}
