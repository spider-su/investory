package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.ProjectedIncomePolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PlanEditorInputTestFactory {
  private PlanEditorInputTestFactory() {}

  static PlanEditorInput input(Map<String, String> values) {
    return new PlanEditorInput(
        integer(values, "ageAtPlanStart"),
        integer(values, "startYear"),
        integer(values, "endAge"),
        integer(values, "retirementAge"),
        decimal(values, "monthlyLivingCosts"),
        decimal(values, "discretionaryExpenses"),
        decimal(values, "inflation"),
        decimal(values, "fixedIncomeReturn"),
        decimal(values, "rentalIncomeGrowthSpread"),
        decimal(values, "spendingGrowthSpread"),
        mode(values, "rentalIncomeMode"),
        decimal(values, "manualRentalIncome"),
        mode(values, "bondCashIncomeMode"),
        decimal(values, "manualBondCashIncome"),
        decimal(values, "equityReturn"),
        decimal(values, "safeReserveYears"),
        decimal(values, "equityHarvestThreshold"),
        decimal(values, "equityHarvestShare"),
        bool(values, "allowEmergencyEquityWithdrawal"),
        decimal(values, "annualEmploymentIncome"),
        decimal(values, "annualPreRetirementContribution"),
        decimal(values, "annualPension"),
        integer(values, "pensionStartAge"),
        expenseProfile(values.get("expenseProfile")));
  }

  private static Integer integer(Map<String, String> values, String name) {
    String value = values.get(name);
    if (value == null || value.isBlank()) return null;
    try {
      return new BigDecimal(value).intValueExact();
    } catch (NumberFormatException | ArithmeticException exception) {
      throw new IllegalArgumentException("Invalid integer " + name, exception);
    }
  }

  private static BigDecimal decimal(Map<String, String> values, String name) {
    String value = values.get(name);
    return value == null || value.isBlank() ? null : new BigDecimal(value);
  }

  private static Boolean bool(Map<String, String> values, String name) {
    String value = values.get(name);
    return value == null || value.isBlank() ? null : Boolean.parseBoolean(value);
  }

  private static ProjectedIncomePolicy.IncomeMode mode(Map<String, String> values, String name) {
    String value = values.get(name);
    return value == null || value.isBlank()
        ? null
        : ProjectedIncomePolicy.IncomeMode.valueOf(value.trim().toUpperCase());
  }

  private static List<PlanEditorInput.ExpenseStageInput> expenseProfile(String raw) {
    if (raw == null || raw.isBlank()) return null;
    List<PlanEditorInput.ExpenseStageInput> result = new ArrayList<>();
    for (String entry : raw.split(";")) {
      String[] parts = entry.split(":", -1);
      if (parts.length != 2) throw new IllegalArgumentException("Invalid expense profile");
      try {
        result.add(
            new PlanEditorInput.ExpenseStageInput(
                new BigDecimal(parts[0].trim()).intValueExact(), new BigDecimal(parts[1].trim())));
      } catch (NumberFormatException | ArithmeticException exception) {
        throw new IllegalArgumentException("Invalid expense profile", exception);
      }
    }
    return List.copyOf(result);
  }
}
