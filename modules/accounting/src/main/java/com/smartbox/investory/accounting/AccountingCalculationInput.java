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
    AccountingPeriodContext periodContext) {
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
        AccountingPeriodContext.compatibility(period, profile));
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
  }

  public record CalculationAdjustments(
      java.math.BigDecimal revenueNetPln, java.math.BigDecimal salesVat) {
    public static CalculationAdjustments none() {
      return new CalculationAdjustments(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
    }
  }
}
