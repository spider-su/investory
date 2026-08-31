package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ExpenseProfile;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.ProjectedIncomePolicy;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationFundingStrategy;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Year;
import java.util.List;

/** Maps HTTP form/query values into canonical simulation assumptions. */
final class SimulationRequestMapper {
  private final RetirementPresentationClient presentation;
  private final RetirementPlanInputClient planInput;
  private final Clock clock;

  SimulationRequestMapper(
      RetirementPresentationClient presentation, RetirementPlanInputClient planInput, Clock clock) {
    this.presentation = presentation;
    this.planInput = planInput;
    this.clock = clock;
  }

  SimulationAssumptions applyLegacyOverrides(
      SimulationAssumptions base, LegacyQueryOverrides input) {
    CurrencyType submittedCurrency =
        input.submittedDisplayCurrency() == null
            ? input.displayCurrency()
            : input.submittedDisplayCurrency();
    return base.toBuilder()
        .currentAge(input.currentAge() == null ? base.currentAge() : input.currentAge())
        .endAge(input.endAge() == null ? base.endAge() : input.endAge())
        .annualLivingExpenses(
            resolveDisplayedMoney(
                input.annualExpenses(),
                input.annualExpensesCanonical(),
                input.annualExpensesEdited(),
                submittedCurrency,
                base.annualLivingExpenses()))
        .inflationRate(rate(input.inflation(), base.inflationRate()))
        .fixedIncomeReturnRate(rate(input.fixedIncomeReturn(), base.fixedIncomeReturnRate()))
        .equityReturnRate(rate(input.equityReturn(), base.equityReturnRate()))
        .pensionStartAge(
            input.pensionStartAge() == null ? base.pensionStartAge() : input.pensionStartAge())
        .annualPension(
            resolveDisplayedMoney(
                input.annualPension(),
                input.annualPensionCanonical(),
                input.annualPensionEdited(),
                submittedCurrency,
                base.annualPension()))
        .capitalGainTaxRate(rate(input.capitalGainTaxRate(), base.capitalGainTaxRate()))
        .annualDiscretionaryExpenses(
            resolveDisplayedMoney(
                input.discretionaryExpenses(),
                input.discretionaryExpensesCanonical(),
                input.discretionaryExpensesEdited(),
                submittedCurrency,
                base.annualDiscretionaryExpenses()))
        .rentalIncomeGrowthSpread(
            rate(input.rentalIncomeGrowthSpread(), base.rentalIncomeGrowthSpread()))
        .spendingGrowthSpread(rate(input.spendingGrowthSpread(), base.spendingGrowthSpread()))
        .fundingStrategy(
            input.fundingStrategy() == null ? base.fundingStrategy() : input.fundingStrategy())
        .safeReserveYears(
            input.safeReserveYears() == null ? base.safeReserveYears() : input.safeReserveYears())
        .equityHarvestMinimumReturnRate(
            input.equityHarvestMinimumReturn() == null
                ? base.equityHarvestMinimumReturnRate()
                : rate(input.equityHarvestMinimumReturn(), base.equityHarvestMinimumReturnRate()))
        .equityGainHarvestRate(
            input.equityGainHarvest() == null
                ? base.equityGainHarvestRate()
                : rate(input.equityGainHarvest(), base.equityGainHarvestRate()))
        .allowEmergencyEquityWithdrawal(
            input.allowEmergencyEquityWithdrawal() == null
                ? base.allowEmergencyEquityWithdrawal()
                : input.allowEmergencyEquityWithdrawal())
        .fundingOrder(
            input.fundingOrder() == null
                ? base.fundingOrder()
                : SimulationInputParser.parseFundingOrder(input.fundingOrder()))
        .build();
  }

  SimulationAssumptions mapSaveForm(SimulationAssumptions storedAssumptions, SavePlanForm input) {
    return mapSaveForm(storedAssumptions, input, Year.now(clock).getValue());
  }

  SimulationAssumptions mapSaveForm(
      SimulationAssumptions storedAssumptions, SavePlanForm input, int currentYear) {
    int ageAtPlanStart =
        input.ageAtPlanStart() == null ? input.currentAge() : input.ageAtPlanStart();
    int startYear =
        input.startYear() == null
            ? storedAssumptions == null ? Year.now(clock).getValue() : storedAssumptions.startYear()
            : input.startYear();
    int retirementAge = input.retirementAge() == null ? ageAtPlanStart : input.retirementAge();
    validateTemporalAnchor(startYear, ageAtPlanStart, input.endAge(), retirementAge, currentYear);

    BigDecimal annualLivingCosts =
        input.monthlyLivingCosts() == null
            ? input.annualExpenses()
            : input.monthlyLivingCosts().multiply(BigDecimal.valueOf(12));
    if (annualLivingCosts == null) annualLivingCosts = BigDecimal.ZERO;

    SimulationAssumptions base =
        storedAssumptions == null
            ? SimulationAssumptions.defaults(ageAtPlanStart, input.endAge(), startYear)
            : storedAssumptions;
    SimulationAssumptions mappedAssumptions =
        base.toBuilder()
            .currentAge(ageAtPlanStart)
            .endAge(input.endAge())
            .annualLivingExpenses(
                presentation.fromDisplay(
                    annualLivingCosts, input.displayCurrency(), BigDecimal.ZERO))
            .inflationRate(rate(input.inflation(), BigDecimal.ZERO))
            .fixedIncomeReturnRate(rate(input.fixedIncomeReturn(), base.fixedIncomeReturnRate()))
            .equityReturnRate(rate(input.equityReturn(), BigDecimal.ZERO))
            .pensionStartAge(normalizePensionStartAge(input.pensionStartAge()))
            .annualPension(
                presentation.fromDisplay(
                    input.annualPension(), input.displayCurrency(), BigDecimal.ZERO))
            .capitalGainTaxRate(rate(input.capitalGainTaxRate(), BigDecimal.ZERO))
            .startYear(startYear)
            .annualDiscretionaryExpenses(
                presentation.fromDisplay(
                    input.discretionaryExpenses(), input.displayCurrency(), BigDecimal.ZERO))
            .futureEvents(
                storedAssumptions == null ? java.util.List.of() : storedAssumptions.futureEvents())
            .rentalIncomeGrowthSpread(
                rate(
                    input.rentalIncomeGrowthSpread(),
                    SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_SPREAD))
            .spendingGrowthSpread(
                rate(
                    input.spendingGrowthSpread(),
                    SimulationAssumptions.DEFAULT_SPENDING_GROWTH_SPREAD))
            .fundingStrategy(SimulationFundingStrategy.SIMPLE_WATERFALL)
            .safeReserveYears(
                input.safeReserveYears() == null
                    ? base.safeReserveYears()
                    : input.safeReserveYears())
            .equityHarvestMinimumReturnRate(
                rate(
                    input.equityHarvestMinimumReturn(),
                    SimulationAssumptions.DEFAULT_EQUITY_HARVEST_MINIMUM_RETURN_RATE))
            .equityGainHarvestRate(
                rate(
                    input.equityGainHarvest(),
                    SimulationAssumptions.DEFAULT_EQUITY_GAIN_HARVEST_RATE))
            .allowEmergencyEquityWithdrawal(input.allowEmergencyEquityWithdrawal())
            .retirementAge(retirementAge)
            .annualEmploymentIncome(
                presentation.fromDisplay(
                    input.annualEmploymentIncome(), input.displayCurrency(), BigDecimal.ZERO))
            .annualPreRetirementContribution(
                presentation.fromDisplay(
                    input.annualPreRetirementContribution(),
                    input.displayCurrency(),
                    BigDecimal.ZERO))
            .fundingOrder(SimulationInputParser.parseFundingOrder(input.fundingOrder()))
            .expenseProfile(ExpenseProfile.EMPTY)
            .projectedIncomePolicy(
                new ProjectedIncomePolicy(
                    incomeMode(input.rentalIncomeMode()),
                    presentation.fromDisplay(
                        input.manualRentalIncome(), input.displayCurrency(), null),
                    incomeMode(input.bondCashIncomeMode()),
                    presentation.fromDisplay(
                        input.manualBondCashIncome(), input.displayCurrency(), null)))
            .build();

    if (input.monthlyLivingCosts() == null && input.annualExpenses() != null) {
      return mappedAssumptions.toBuilder()
          .expenseProfile(SimulationInputParser.parseExpenseProfile(input.expenseProfile()))
          .build();
    }
    return planInput
        .normalizePlanEditorInput(
            editorInput(input, ageAtPlanStart, startYear, retirementAge, annualLivingCosts),
            mappedAssumptions,
            input.displayCurrency())
        .assumptions();
  }

  BigDecimal resolveDisplayedMoney(
      BigDecimal displayAmount,
      BigDecimal canonicalAmount,
      boolean edited,
      CurrencyType displayCurrency,
      BigDecimal fallback) {
    return !edited && canonicalAmount != null
        ? canonicalAmount
        : displayAmount == null
            ? fallback
            : presentation.fromDisplay(displayAmount, displayCurrency, fallback);
  }

  private PlanEditorInput editorInput(
      SavePlanForm input,
      int ageAtPlanStart,
      int startYear,
      int retirementAge,
      BigDecimal annualLivingCosts) {
    BigDecimal monthlyLivingCosts =
        input.monthlyLivingCosts() == null
            ? annualLivingCosts.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP)
            : input.monthlyLivingCosts();
    List<PlanEditorInput.ExpenseStageInput> expenseProfile =
        input.expenseProfile() == null || input.expenseProfile().isBlank()
            ? null
            : java.util.Arrays.stream(input.expenseProfile().split(";"))
                .map(entry -> entry.split(":", -1))
                .map(
                    parts ->
                        new PlanEditorInput.ExpenseStageInput(
                            Integer.parseInt(parts[0].trim()), new BigDecimal(parts[1].trim())))
                .toList();
    return new PlanEditorInput(
        ageAtPlanStart,
        startYear,
        input.endAge(),
        retirementAge,
        monthlyLivingCosts,
        input.discretionaryExpenses(),
        input.inflation(),
        input.fixedIncomeReturn(),
        input.rentalIncomeGrowthSpread(),
        input.spendingGrowthSpread(),
        incomeMode(input.rentalIncomeMode()),
        input.manualRentalIncome(),
        incomeMode(input.bondCashIncomeMode()),
        input.manualBondCashIncome(),
        input.equityReturn(),
        input.safeReserveYears(),
        input.equityHarvestMinimumReturn(),
        input.equityGainHarvest(),
        input.allowEmergencyEquityWithdrawal(),
        input.annualEmploymentIncome(),
        input.annualPreRetirementContribution(),
        input.annualPension(),
        input.pensionStartAge(),
        expenseProfile);
  }

  private void validateTemporalAnchor(
      int startYear, int ageAtPlanStart, int endAge, int retirementAge, int currentYear) {
    int currentPlanningAge = ageAtPlanStart + currentYear - startYear;
    if (startYear > currentYear)
      throw new IllegalArgumentException("Plan start year cannot be in the future");
    if (ageAtPlanStart < 0 || endAge < currentPlanningAge)
      throw new IllegalArgumentException("Invalid plan temporal ages");
    if (retirementAge < ageAtPlanStart || retirementAge > endAge)
      throw new IllegalArgumentException("Invalid retirement age");
  }

  private static BigDecimal rate(BigDecimal percent, BigDecimal fallback) {
    return percent == null ? fallback : percent.movePointLeft(2);
  }

  static BigDecimal percentInputToRate(BigDecimal percent, BigDecimal fallback) {
    return rate(percent, fallback);
  }

  private static ProjectedIncomePolicy.IncomeMode incomeMode(String value) {
    return ProjectedIncomePolicy.IncomeMode.valueOf(
        value.trim().toUpperCase(java.util.Locale.ROOT));
  }

  private static Integer normalizePensionStartAge(Integer pensionStartAge) {
    return pensionStartAge;
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  record LegacyQueryOverrides(
      Integer currentAge,
      Integer endAge,
      BigDecimal annualExpenses,
      BigDecimal annualExpensesCanonical,
      boolean annualExpensesEdited,
      BigDecimal discretionaryExpenses,
      BigDecimal discretionaryExpensesCanonical,
      boolean discretionaryExpensesEdited,
      BigDecimal inflation,
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal spendingGrowthSpread,
      SimulationFundingStrategy fundingStrategy,
      String fundingOrder,
      BigDecimal safeReserveYears,
      BigDecimal equityHarvestMinimumReturn,
      BigDecimal equityGainHarvest,
      Boolean allowEmergencyEquityWithdrawal,
      BigDecimal fixedIncomeReturn,
      BigDecimal equityReturn,
      Integer pensionStartAge,
      BigDecimal annualPension,
      BigDecimal annualPensionCanonical,
      boolean annualPensionEdited,
      BigDecimal capitalGainTaxRate,
      CurrencyType displayCurrency,
      CurrencyType submittedDisplayCurrency) {}

  record SavePlanForm(
      int currentAge,
      Integer ageAtPlanStart,
      Integer startYear,
      int endAge,
      Integer retirementAge,
      BigDecimal annualEmploymentIncome,
      BigDecimal annualPreRetirementContribution,
      BigDecimal annualExpenses,
      BigDecimal monthlyLivingCosts,
      BigDecimal discretionaryExpenses,
      BigDecimal inflation,
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal spendingGrowthSpread,
      String rentalIncomeMode,
      BigDecimal manualRentalIncome,
      String bondCashIncomeMode,
      BigDecimal manualBondCashIncome,
      SimulationFundingStrategy fundingStrategy,
      String fundingOrder,
      String expenseProfile,
      BigDecimal safeReserveYears,
      BigDecimal equityHarvestMinimumReturn,
      BigDecimal equityGainHarvest,
      boolean allowEmergencyEquityWithdrawal,
      BigDecimal fixedIncomeReturn,
      BigDecimal equityReturn,
      Integer pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      CurrencyType displayCurrency) {}
}
