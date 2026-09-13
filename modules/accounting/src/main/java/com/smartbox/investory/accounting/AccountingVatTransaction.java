package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Normalized, explicitly classified VAT transaction. */
public record AccountingVatTransaction(
    LocalDate taxDate,
    String sourceDocumentId,
    String reference,
    Direction direction,
    VatTreatment treatment,
    String counterpartyCountry,
    String counterpartyTaxIdentifier,
    String identifierType,
    String vatEuNumber,
    LocalDate viesVerifiedAt,
    String viesStatus,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal deductibleVat,
    String evidence) {
  public enum Direction {
    SALE,
    PURCHASE
  }
}
