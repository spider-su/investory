package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.AnnualEconomicsView;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Pure calculations for Long-Term asset income, expenses, tax, and yield. */
final class LongTermAssetEconomics {
  private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

  private LongTermAssetEconomics() {}

  static RentalEconomics rental(
      List<RentalContractModel.Term> terms, BigDecimal annualTaxBase, BigDecimal value) {
    BigDecimal income = BigDecimal.ZERO;
    BigDecimal expenses = BigDecimal.ZERO;
    BigDecimal monthlyPayment = BigDecimal.ZERO;
    for (var term : terms) {
      BigDecimal annual = annualize(term.amount(), term.frequency());
      if (isRentalIncome(term.type())) {
        income = income.add(annual);
      } else if (isRentalExpense(term.type())) {
        if (!term.paidByTenant()) {
          expenses = expenses.add(annual);
        }
      }
      if (isRentalIncome(term.type())) {
        monthlyPayment = monthlyPayment.add(monthlyAmount(term.amount(), term.frequency()));
      }
    }
    BigDecimal normalizedTaxBase = annualTaxBase == null ? BigDecimal.ZERO : annualTaxBase;
    BigDecimal tax = normalizedTaxBase.multiply(FinancialPolicyDefaults.RENTAL_TAX_RATE);
    return new RentalEconomics(
        economics(
            income,
            expenses,
            value,
            tax,
            normalizedTaxBase.divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP)),
        monthlyPayment);
  }

  static AnnualEconomicsView economics(BigDecimal gross, BigDecimal expenses, BigDecimal value) {
    return economics(
        gross, expenses, value, gross.multiply(FinancialPolicyDefaults.GLOBAL_PROFIT_TAX_RATE));
  }

  static AnnualEconomicsView economics(
      BigDecimal gross, BigDecimal expenses, BigDecimal value, BigDecimal tax) {
    return economics(gross, expenses, value, tax, BigDecimal.ZERO);
  }

  static BigDecimal accruedAmount(
      BigDecimal amount, Frequency frequency, LocalDate start, LocalDate end) {
    if (amount == null || start == null || end == null || start.isAfter(end)) {
      return BigDecimal.ZERO;
    }
    if (frequency == Frequency.ANNUAL) {
      BigDecimal accrued = BigDecimal.ZERO;
      LocalDate from = start;
      while (!from.isAfter(end)) {
        LocalDate yearEnd = LocalDate.of(from.getYear(), 12, 31);
        LocalDate to = end.isBefore(yearEnd) ? end : yearEnd;
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        accrued =
            accrued.add(
                amount
                    .multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(from.lengthOfYear()), 12, RoundingMode.HALF_UP));
        from = to.plusDays(1);
      }
      return accrued;
    }
    BigDecimal accrued = BigDecimal.ZERO;
    LocalDate month = start.withDayOfMonth(1);
    LocalDate lastMonth = end.withDayOfMonth(1);
    while (!month.isAfter(lastMonth)) {
      LocalDate from = start.isAfter(month) ? start : month;
      LocalDate monthEnd = month.withDayOfMonth(month.lengthOfMonth());
      LocalDate to = end.isBefore(monthEnd) ? end : monthEnd;
      long coveredDays = java.time.temporal.ChronoUnit.DAYS.between(from, to.plusDays(1));
      accrued =
          accrued.add(
              amount
                  .multiply(BigDecimal.valueOf(coveredDays))
                  .divide(BigDecimal.valueOf(month.lengthOfMonth()), 12, RoundingMode.HALF_UP));
      month = month.plusMonths(1);
    }
    return accrued;
  }

  static AnnualEconomicsView economics(
      BigDecimal gross,
      BigDecimal expenses,
      BigDecimal value,
      BigDecimal tax,
      BigDecimal monthlyTaxBase) {
    BigDecimal beforeTax = gross.subtract(expenses);
    BigDecimal afterTax = beforeTax.subtract(tax);
    return new AnnualEconomicsView(
        gross,
        expenses,
        tax,
        monthlyTaxBase,
        tax.divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP),
        beforeTax,
        afterTax,
        afterTax.divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP),
        calculateYield(gross, value),
        calculateYield(beforeTax, value),
        calculateYield(afterTax, value));
  }

  static BigDecimal annualize(BigDecimal amount, Frequency frequency) {
    if (amount == null) return BigDecimal.ZERO;
    return switch (frequency) {
      case MONTHLY -> amount.multiply(MONTHS_PER_YEAR);
      case ANNUAL -> amount;
    };
  }

  static BigDecimal monthlyTenantPayment(
      CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {
    if (amount == null || (!isRentalIncome(type) && !paidByTenant)) {
      return BigDecimal.ZERO;
    }
    return monthlyAmount(amount, frequency);
  }

  private static BigDecimal monthlyAmount(BigDecimal amount, Frequency frequency) {
    return switch (frequency) {
      case MONTHLY -> amount;
      case ANNUAL -> amount.divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP);
    };
  }

  private static BigDecimal calculateYield(BigDecimal amount, BigDecimal value) {
    return value.signum() == 0 ? BigDecimal.ZERO : amount.divide(value, 12, RoundingMode.HALF_UP);
  }

  static boolean isRentalExpense(CashFlowType type) {
    return switch (type) {
      case ADMIN_FEE, UTILITIES, PROPERTY_TAX, INSURANCE, OTHER_EXPENSE -> true;
      case RENT, PARKING_RENT, OTHER_INCOME -> false;
    };
  }

  static boolean isRentalIncome(CashFlowType type) {
    return switch (type) {
      case RENT, PARKING_RENT, OTHER_INCOME -> true;
      case ADMIN_FEE, UTILITIES, PROPERTY_TAX, INSURANCE, OTHER_EXPENSE -> false;
    };
  }

  record RentalEconomics(AnnualEconomicsView economics, BigDecimal monthlyPayment) {}
}
