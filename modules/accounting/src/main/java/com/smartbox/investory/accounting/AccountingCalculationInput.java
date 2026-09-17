package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import java.time.LocalDate;
import java.util.List;

/** Normalized facts needed by the accounting calculation. It contains no operational state. */
public record AccountingCalculationInput(
    LocalDate period,
    List<InvoiceRow> invoices,
    List<ExpenseRow> expenses,
    List<TaxInputRow> taxInputs,
    AccountingProfile profile,
    CalculationAdjustments adjustments,
    AccountingPeriodContext periodContext,
    List<AccountingVatTransaction> vatTransactions,
    AccountingCalculationMode calculationMode,
    List<AccountingVatAdjustment> vatAdjustments) {
  public AccountingCalculationInput(
      LocalDate period,
      List<InvoiceRow> invoices,
      List<ExpenseRow> expenses,
      List<TaxInputRow> taxInputs,
      AccountingProfile profile,
      CalculationAdjustments adjustments,
      AccountingPeriodContext periodContext,
      List<AccountingVatTransaction> vatTransactions,
      AccountingCalculationMode calculationMode) {
    this(
        period,
        invoices,
        expenses,
        taxInputs,
        profile,
        adjustments,
        periodContext,
        vatTransactions,
        calculationMode,
        List.of());
  }

  public AccountingCalculationInput(
      LocalDate period,
      List<InvoiceRow> invoices,
      List<ExpenseRow> expenses,
      List<TaxInputRow> taxInputs,
      AccountingProfile profile,
      CalculationAdjustments adjustments) {
    this(
        period,
        invoices,
        expenses,
        taxInputs,
        profile,
        adjustments,
        AccountingPeriodContext.compatibility(period, profile),
        List.of(),
        AccountingCalculationMode.HISTORICAL_RECONSTRUCTION,
        List.of());
  }

  public AccountingCalculationInput(
      LocalDate period,
      List<InvoiceRow> invoices,
      List<ExpenseRow> expenses,
      List<TaxInputRow> taxInputs,
      AccountingProfile profile,
      CalculationAdjustments adjustments,
      AccountingPeriodContext periodContext) {
    this(
        period,
        invoices,
        expenses,
        taxInputs,
        profile,
        adjustments,
        periodContext,
        List.of(),
        AccountingCalculationMode.HISTORICAL_RECONSTRUCTION,
        List.of());
  }

  public AccountingCalculationInput(
      LocalDate period,
      List<InvoiceRow> invoices,
      List<ExpenseRow> expenses,
      List<TaxInputRow> taxInputs,
      AccountingProfile profile,
      CalculationAdjustments adjustments,
      AccountingPeriodContext periodContext,
      List<AccountingVatTransaction> vatTransactions) {
    this(
        period,
        invoices,
        expenses,
        taxInputs,
        profile,
        adjustments,
        periodContext,
        vatTransactions,
        AccountingCalculationMode.HISTORICAL_RECONSTRUCTION,
        List.of());
  }

  public AccountingCalculationInput {
    invoices = List.copyOf(invoices);
    expenses = List.copyOf(expenses);
    taxInputs = List.copyOf(taxInputs);
    adjustments = adjustments == null ? CalculationAdjustments.none() : adjustments;
    periodContext =
        periodContext == null
            ? AccountingPeriodContext.compatibility(period, profile)
            : periodContext;
    vatTransactions = vatTransactions == null ? List.of() : List.copyOf(vatTransactions);
    vatAdjustments = vatAdjustments == null ? List.of() : List.copyOf(vatAdjustments);
    calculationMode =
        calculationMode == null
            ? AccountingCalculationMode.HISTORICAL_RECONSTRUCTION
            : calculationMode;
  }

  public record CalculationAdjustments(
      java.math.BigDecimal revenueNetPln, java.math.BigDecimal salesVat) {
    public static CalculationAdjustments none() {
      return new CalculationAdjustments(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
    }
  }
}
