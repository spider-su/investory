package com.smartbox.investory.poc.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AccountingMonthSnapshot(
    LocalDate period,
    BigDecimal domesticRevenueNetPln,
    BigDecimal foreignBookedRevenuePln,
    BigDecimal foreignSourceRevenueEur,
    BigDecimal totalBookedRevenuePln,
    FxCalculation fx,
    RyczałtCalculation ryczalt,
    VatCalculation vat,
    List<InvoiceRow> invoices,
    List<ExpenseRow> expenses,
    List<ReconciliationRow> reconciliations,
    List<ObligationRow> obligations,
    List<BankRow> bankTransactions) {

  public record InvoiceRow(
      long id,
      LocalDate taxPeriod,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate fxRateDate,
      String reference,
      String customerAlias,
      String invoiceKind,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal correctionNetAmount,
      BigDecimal correctionVatAmount,
      BigDecimal correctionGrossAmount,
      BigDecimal expectedReceivable,
      BigDecimal bookedNetPln,
      BigDecimal ryczaltRate,
      String note) {}

  public record ExpenseRow(
      long id,
      LocalDate taxPeriod,
      LocalDate invoiceDate,
      String reference,
      String supplierAlias,
      String category,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal vatDeductionRatio,
      BigDecimal deductibleVat,
      String sourceQuality,
      String note) {}

  public record BankRow(
      long id,
      LocalDate bookingDate,
      LocalDate relatedPeriod,
      String reference,
      String counterpartyAlias,
      String currency,
      BigDecimal amount,
      String transactionType,
      String scope,
      String note) {}

  public record ObligationRow(
      String obligationType,
      LocalDate dueDate,
      BigDecimal expectedAmount,
      BigDecimal paidAmount,
      LocalDate paymentDate,
      String status,
      String note) {}

  public record TaxInputRow(String inputType, BigDecimal amount, String note) {}

  public record ReconciliationRow(
      String reference,
      String kind,
      BigDecimal expectedAmount,
      String currency,
      BigDecimal matchedAmount,
      LocalDate paymentDate,
      String status,
      String explanation) {}

  public record FxCalculation(
      LocalDate rateDate,
      BigDecimal sourceEur,
      BigDecimal calculatedPln,
      BigDecimal expectedPln,
      BigDecimal difference,
      String status) {}

  public record RyczałtCalculation(
      BigDecimal revenueBeforeDeductions,
      BigDecimal julyOnlyCorrectionNetAdjustment,
      BigDecimal healthContributionPaid,
      BigDecimal healthDeduction,
      BigDecimal taxableBase,
      BigDecimal rate,
      BigDecimal calculatedTax,
      BigDecimal expectedTax,
      BigDecimal difference,
      String status) {}

  public record VatCalculation(
      BigDecimal outputVatBeforeJulyCorrection,
      BigDecimal julyOnlySalesCorrectionVat,
      BigDecimal outputVatAfterSalesCorrection,
      BigDecimal deductibleInputVat,
      BigDecimal julyOnlyVatCorrectionAdjustment,
      BigDecimal calculatedVat,
      BigDecimal expectedVat,
      BigDecimal difference,
      String status) {}
}
