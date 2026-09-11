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
    List<InvoiceRow> invoices,
    List<ReconciliationRow> reconciliations,
    List<ObligationRow> obligations,
    List<BankRow> bankTransactions) {

  public record InvoiceRow(
      long id,
      LocalDate taxPeriod,
      LocalDate issueDate,
      LocalDate saleDate,
      String reference,
      String customerAlias,
      String invoiceKind,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal correctionGrossAmount,
      BigDecimal expectedReceivable,
      BigDecimal bookedNetPln,
      BigDecimal ryczaltRate,
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

  public record ReconciliationRow(
      String reference,
      String kind,
      BigDecimal expectedAmount,
      String currency,
      BigDecimal matchedAmount,
      LocalDate paymentDate,
      String status,
      String explanation) {}
}
