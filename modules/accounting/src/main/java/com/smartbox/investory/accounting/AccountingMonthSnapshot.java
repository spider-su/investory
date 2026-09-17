package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record AccountingMonthSnapshot(
    LocalDate period,
    BigDecimal domesticRevenueNetPln,
    BigDecimal foreignBookedRevenuePln,
    BigDecimal foreignSourceRevenueEur,
    BigDecimal totalBookedRevenuePln,
    FxCalculation fx,
    RyczaltCalculation ryczalt,
    VatCalculation vat,
    ZusCalculation zus,
    List<ComparisonRow> comparisons,
    List<InvoiceRow> invoices,
    List<ExpenseRow> expenses,
    List<ReconciliationRow> reconciliations,
    List<ObligationRow> obligations,
    List<BankRow> bankTransactions,
    AccountingCalculationMode calculationMode,
    AccountingReadiness readiness,
    List<AccountingIssue> issues) {

  public long salesDocumentCount() {
    return invoices.stream().filter(invoice -> invoice != null).count();
  }

  public long expenseDocumentCount() {
    return expenses.stream().filter(expense -> expense != null).count();
  }

  public BigDecimal salesNetPln() {
    return invoices.stream()
        .map(InvoiceRow::bookedNetPln)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal salesVat() {
    return invoices.stream()
        .map(InvoiceRow::vatAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal expenseNet() {
    return expenses.stream()
        .map(ExpenseRow::netAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal deductibleInputVat() {
    return vat.deductibleInputVat();
  }

  /** Calculated monthly obligations before applying any recorded payments. */
  public BigDecimal totalCalculatedObligations() {
    return vat.calculatedVat().add(ryczalt.calculatedTax()).add(zus.totalZus());
  }

  public long matchedPaymentCount() {
    return reconciliations.stream()
        .filter(row -> "MATCHED".equals(row.status()) || "PAID".equals(row.status()))
        .count();
  }

  public long paymentReviewCount() {
    return reconciliations.stream()
        .filter(row -> "UNMATCHED".equals(row.status()) || "DIFF".equals(row.status()))
        .count();
  }

  public String userStatus() {
    if (!issues.isEmpty()) return "Needs attention";
    return filingConfirmed() ? "Closed" : "Ready to file";
  }

  private boolean filingConfirmed() {
    return obligations.stream().anyMatch(row -> "CONFIRMED".equals(row.status()));
  }

  public AccountingMonthSnapshot(
      LocalDate period,
      BigDecimal domesticRevenueNetPln,
      BigDecimal foreignBookedRevenuePln,
      BigDecimal foreignSourceRevenueEur,
      BigDecimal totalBookedRevenuePln,
      FxCalculation fx,
      RyczaltCalculation ryczalt,
      VatCalculation vat,
      ZusCalculation zus,
      List<ComparisonRow> comparisons,
      List<InvoiceRow> invoices,
      List<ExpenseRow> expenses,
      List<ReconciliationRow> reconciliations,
      List<ObligationRow> obligations,
      List<BankRow> bankTransactions) {
    this(
        period,
        domesticRevenueNetPln,
        foreignBookedRevenuePln,
        foreignSourceRevenueEur,
        totalBookedRevenuePln,
        fx,
        ryczalt,
        vat,
        zus,
        comparisons,
        invoices,
        expenses,
        reconciliations,
        obligations,
        bankTransactions,
        AccountingCalculationMode.HISTORICAL_RECONSTRUCTION,
        AccountingReadiness.READY,
        List.of());
  }

  public record ComparisonRow(
      String area,
      BigDecimal calculated,
      BigDecimal expected,
      BigDecimal difference,
      String currency,
      String status,
      String note) {}

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
      String note,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String ksefNumber,
      AccountingFilingEvidence filingEvidence) {
    public InvoiceRow(
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
        String note) {
      this(
          id,
          taxPeriod,
          issueDate,
          saleDate,
          fxRateDate,
          reference,
          customerAlias,
          invoiceKind,
          currency,
          netAmount,
          vatAmount,
          grossAmount,
          correctionNetAmount,
          correctionVatAmount,
          correctionGrossAmount,
          expectedReceivable,
          bookedNetPln,
          ryczaltRate,
          note,
          null,
          null,
          null,
          null);
    }
  }

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
      String note,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String ksefNumber,
      AccountingFilingEvidence filingEvidence) {
    public ExpenseRow(
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
        String note) {
      this(
          id,
          taxPeriod,
          invoiceDate,
          reference,
          supplierAlias,
          category,
          currency,
          netAmount,
          vatAmount,
          grossAmount,
          vatDeductionRatio,
          deductibleVat,
          sourceQuality,
          note,
          null,
          null,
          null,
          null);
    }
  }

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
      LocalDate taxPeriod,
      String obligationType,
      LocalDate dueDate,
      BigDecimal expectedAmount,
      BigDecimal paidAmount,
      LocalDate paymentDate,
      String status,
      String note) {
    public ObligationRow(
        String obligationType,
        LocalDate dueDate,
        BigDecimal expectedAmount,
        BigDecimal paidAmount,
        LocalDate paymentDate,
        String status,
        String note) {
      this(null, obligationType, dueDate, expectedAmount, paidAmount, paymentDate, status, note);
    }
  }

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
      String status,
      List<String> unavailableInvoiceReferences) {
    public FxCalculation(
        LocalDate rateDate,
        BigDecimal sourceEur,
        BigDecimal calculatedPln,
        BigDecimal expectedPln,
        BigDecimal difference,
        String status) {
      this(rateDate, sourceEur, calculatedPln, expectedPln, difference, status, List.of());
    }
  }

  public record RyczaltCalculation(
      BigDecimal revenueBeforeDeductions,
      BigDecimal socialContributionDeduction,
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
      BigDecimal explicitVatAdjustments,
      BigDecimal calculatedVat,
      BigDecimal expectedVat,
      BigDecimal difference,
      String status) {
    public VatCalculation(
        BigDecimal outputVatBeforeJulyCorrection,
        BigDecimal julyOnlySalesCorrectionVat,
        BigDecimal outputVatAfterSalesCorrection,
        BigDecimal deductibleInputVat,
        BigDecimal julyOnlyVatCorrectionAdjustment,
        BigDecimal calculatedVat,
        BigDecimal expectedVat,
        BigDecimal difference,
        String status) {
      this(
          outputVatBeforeJulyCorrection,
          julyOnlySalesCorrectionVat,
          outputVatAfterSalesCorrection,
          deductibleInputVat,
          julyOnlyVatCorrectionAdjustment,
          BigDecimal.ZERO,
          calculatedVat,
          expectedVat,
          difference,
          status);
    }
  }

  public record ZusCalculation(
      BigDecimal socialZus,
      BigDecimal healthZus,
      BigDecimal totalZus,
      boolean hasUop,
      String socialZusReasonCode) {
    public static final String UOP_PRIMARY_INSURANCE = "UOP_PRIMARY_INSURANCE";
    public static final String JDG_PRIMARY_INSURANCE = "JDG_PRIMARY_INSURANCE";

    public String socialZusReason() {
      return switch (socialZusReasonCode) {
        case UOP_PRIMARY_INSURANCE ->
            "Qualifying UoP is the primary social-insurance title; compulsory JDG social ZUS is not due.";
        case JDG_PRIMARY_INSURANCE ->
            "JDG is the primary social-insurance title; compulsory JDG social ZUS applies.";
        default -> "Unknown social-ZUS applicability reason: " + socialZusReasonCode;
      };
    }
  }
}
