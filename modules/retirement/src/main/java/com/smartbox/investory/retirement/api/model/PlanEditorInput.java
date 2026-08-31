package com.smartbox.investory.retirement.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** Typed user-facing plan editor input. Form-string parsing belongs only at the MVC edge. */
public record PlanEditorInput(
    @NotNull @Min(0) @Max(150) Integer ageAtPlanStart,
    @NotNull @Min(1900) @Max(9999) Integer startYear,
    @NotNull @Min(0) @Max(150) Integer endAge,
    @Min(0) @Max(150) Integer retirementAge,
    BigDecimal monthlyLivingCosts,
    BigDecimal discretionaryExpenses,
    @NotNull BigDecimal inflation,
    BigDecimal fixedIncomeReturn,
    BigDecimal rentalIncomeGrowthSpread,
    BigDecimal spendingGrowthSpread,
    ProjectedIncomePolicy.IncomeMode rentalIncomeMode,
    BigDecimal manualRentalIncome,
    ProjectedIncomePolicy.IncomeMode bondCashIncomeMode,
    BigDecimal manualBondCashIncome,
    @NotNull BigDecimal equityReturn,
    BigDecimal safeReserveYears,
    BigDecimal equityHarvestThreshold,
    BigDecimal equityHarvestShare,
    Boolean allowEmergencyEquityWithdrawal,
    BigDecimal annualEmploymentIncome,
    BigDecimal annualPreRetirementContribution,
    BigDecimal annualPension,
    Integer pensionStartAge,
    List<@Valid ExpenseStageInput> expenseProfile) {

  public PlanEditorInput {
    expenseProfile = expenseProfile == null ? null : List.copyOf(expenseProfile);
  }

  /** Compatibility view used only by the MVC form adapter during migration. */
  public String value(String name) {
    return switch (name) {
      case "ageAtPlanStart" -> text(ageAtPlanStart);
      case "startYear" -> text(startYear);
      case "endAge" -> text(endAge);
      case "retirementAge" -> text(retirementAge);
      case "monthlyLivingCosts" -> text(monthlyLivingCosts);
      case "discretionaryExpenses" -> text(discretionaryExpenses);
      case "inflation" -> text(inflation);
      case "fixedIncomeReturn" -> text(fixedIncomeReturn);
      case "rentalIncomeGrowthSpread" -> text(rentalIncomeGrowthSpread);
      case "spendingGrowthSpread" -> text(spendingGrowthSpread);
      case "equityReturn" -> text(equityReturn);
      case "rentalIncomeMode" -> text(rentalIncomeMode);
      case "bondCashIncomeMode" -> text(bondCashIncomeMode);
      case "manualRentalIncome" -> text(manualRentalIncome);
      case "manualBondCashIncome" -> text(manualBondCashIncome);
      case "safeReserveYears" -> text(safeReserveYears);
      case "equityHarvestThreshold" -> text(equityHarvestThreshold);
      case "equityHarvestShare" -> text(equityHarvestShare);
      case "allowEmergencyEquityWithdrawal" -> text(allowEmergencyEquityWithdrawal);
      case "annualEmploymentIncome" -> text(annualEmploymentIncome);
      case "annualPreRetirementContribution" -> text(annualPreRetirementContribution);
      case "annualPension" -> text(annualPension);
      case "pensionStartAge" -> text(pensionStartAge);
      case "expenseProfile" -> expenseProfileText();
      default -> null;
    };
  }

  private String expenseProfileText() {
    return expenseProfile == null
        ? null
        : expenseProfile.stream()
            .map(stage -> stage.age() + ":" + stage.level())
            .reduce((a, b) -> a + ";" + b)
            .orElse("");
  }

  private static String text(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  public record ExpenseStageInput(int age, @NotNull BigDecimal level) {}
}
