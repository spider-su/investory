package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Filing-only projection. It deliberately excludes reconciliation, readiness and golden fields. */
public record AccountingFilingInput(
    LocalDate period,
    AccountingMonthSnapshot.VatCalculation vat,
    AccountingMonthSnapshot.RyczaltCalculation ryczalt,
    AccountingMonthSnapshot.ZusCalculation zus,
    List<FilingDocument> sales,
    List<FilingDocument> purchases,
    AccountingProfile taxpayer,
    String schemaVersion) {
  public AccountingFilingInput {
    sales = List.copyOf(sales);
    purchases = List.copyOf(purchases);
  }

  public record FilingDocument(
      String reference,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate purchaseDate,
      String counterpartyIdentifier,
      String counterpartyName,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal deductibleVat,
      AccountingFilingEvidence evidence,
      VatTreatment treatment,
      String counterpartyCountry) {}
}
