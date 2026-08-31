package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ExpenseProfile;
import com.smartbox.investory.retirement.api.model.ExpenseProfileStep;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.PlanInputWarning;
import com.smartbox.investory.retirement.api.model.ProjectedIncomePolicy;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

/** Converts semantic browser values into canonical simulation assumptions in one place. */
@Service
public final class PlanEditorInputNormalizer {
  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
  private static final MathContext PERCENTAGE_MATH_CONTEXT =
      new MathContext(18, RoundingMode.HALF_UP);
  private static final BigDecimal NEGATIVE_ONE = BigDecimal.ONE.negate();
  private final PlanningCurrencyPresentationService presentation;
  private final Clock clock;

  public PlanEditorInputNormalizer(PlanningCurrencyPresentationService presentation, Clock clock) {
    this.presentation = presentation;
    this.clock = clock;
  }

  public Normalized normalize(
      PlanEditorInput input, SimulationAssumptions base, CurrencyType displayCurrency) {
    int ageAtStart = integer(input.ageAtPlanStart(), base.ageAtPlanStart());
    int startYear = integer(input.startYear(), base.planStartYear());
    int endAge = integer(input.endAge(), base.endAge());
    int retirementAge = integer(input.retirementAge(), base.retirementAge());
    int currentYear = Year.now(clock).getValue();
    validateTimeline(startYear, ageAtStart, retirementAge, endAge, currentYear);

    BigDecimal inflation = rate(input.inflation(), base.inflationRate());
    BigDecimal rentalSpread =
        rate(input.rentalIncomeGrowthSpread(), base.rentalIncomeGrowthSpread());
    BigDecimal spendingSpread = rate(input.spendingGrowthSpread(), base.spendingGrowthSpread());
    BigDecimal bondReturn = rate(input.fixedIncomeReturn(), base.fixedIncomeReturnRate());
    BigDecimal investmentReturn = rate(input.equityReturn(), base.equityReturnRate());
    BigDecimal effectiveRental = inflation.add(rentalSpread);
    BigDecimal effectiveSpending = inflation.add(spendingSpread);
    BigDecimal harvestThreshold =
        rate(input.equityHarvestThreshold(), base.equityHarvestMinimumReturnRate());
    BigDecimal harvestShare = rate(input.equityHarvestShare(), base.equityGainHarvestRate());
    if (harvestShare == null) harvestShare = base.equityGainHarvestRate();
    if (harvestShare.signum() < 0 || harvestShare.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Harvest share must be between 0 and 100%");
    validRate(inflation, "inflation");
    validRate(bondReturn, "fixedIncomeReturn");
    validRate(investmentReturn, "equityReturn");
    validRate(effectiveRental, "rentalIncomeGrowthSpread");
    validRate(effectiveSpending, "spendingGrowthSpread");

    BigDecimal monthly = money(input.monthlyLivingCosts(), displayCurrency, null);
    BigDecimal annualLiving =
        monthly == null ? base.annualLivingExpenses() : monthly.multiply(BigDecimal.valueOf(12));
    BigDecimal extras =
        money(input.discretionaryExpenses(), displayCurrency, base.annualDiscretionaryExpenses());
    BigDecimal employment =
        money(input.annualEmploymentIncome(), displayCurrency, base.annualEmploymentIncome());
    BigDecimal contribution =
        money(
            input.annualPreRetirementContribution(),
            displayCurrency,
            base.annualPreRetirementContribution());
    BigDecimal pension = money(input.annualPension(), displayCurrency, base.annualPension());
    nonNegative(annualLiving, "monthlyLivingCosts");
    nonNegative(extras, "discretionaryExpenses");
    nonNegative(employment, "annualEmploymentIncome");
    nonNegative(contribution, "annualPreRetirementContribution");
    nonNegative(pension, "annualPension");

    SimulationAssumptions assumptions =
        base.toBuilder()
            .currentAge(ageAtStart)
            .endAge(endAge)
            .annualLivingExpenses(annualLiving)
            .inflationRate(inflation)
            .fixedIncomeReturnRate(bondReturn)
            .equityReturnRate(investmentReturn)
            .pensionStartAge(
                input.pensionStartAge() == null ? base.pensionStartAge() : input.pensionStartAge())
            .annualPension(pension)
            .startYear(startYear)
            .annualDiscretionaryExpenses(extras)
            .rentalIncomeGrowthSpread(rentalSpread)
            .spendingGrowthSpread(spendingSpread)
            .safeReserveYears(
                input.safeReserveYears() == null
                    ? base.safeReserveYears()
                    : input.safeReserveYears())
            .equityHarvestMinimumReturnRate(harvestThreshold)
            .equityGainHarvestRate(harvestShare)
            .allowEmergencyEquityWithdrawal(
                booleanValue(
                    input.allowEmergencyEquityWithdrawal(), base.allowEmergencyEquityWithdrawal()))
            .retirementAge(retirementAge)
            .annualEmploymentIncome(employment)
            .annualPreRetirementContribution(contribution)
            .expenseProfile(
                expenseProfile(input.expenseProfile(), base.expenseProfile(), ageAtStart, endAge))
            .build();
    ProjectedIncomePolicy incomePolicy =
        new ProjectedIncomePolicy(
            input.rentalIncomeMode() == null
                ? base.projectedIncomePolicy().rentalIncomeMode()
                : input.rentalIncomeMode(),
            money(
                input.manualRentalIncome(),
                displayCurrency,
                base.projectedIncomePolicy().manualRentalIncome()),
            input.bondCashIncomeMode() == null
                ? base.projectedIncomePolicy().bondCashIncomeMode()
                : input.bondCashIncomeMode(),
            money(
                input.manualBondCashIncome(),
                displayCurrency,
                base.projectedIncomePolicy().manualBondCashIncome()));
    validateManualIncome(incomePolicy);
    return new Normalized(
        assumptions.toBuilder().projectedIncomePolicy(incomePolicy).build(),
        warnings(
            inflation,
            investmentReturn,
            effectiveRental,
            effectiveSpending,
            assumptions.expenseProfile(),
            ageAtStart,
            ageAtStart + currentYear - startYear,
            retirementAge));
  }

  private static void validateManualIncome(ProjectedIncomePolicy policy) {
    if (policy.rentalIncomeMode() == ProjectedIncomePolicy.IncomeMode.MANUAL
        && policy.manualRentalIncome() == null) {
      throw new IllegalArgumentException("Manual rental cash income is required");
    }
    if (policy.bondCashIncomeMode() == ProjectedIncomePolicy.IncomeMode.MANUAL
        && policy.manualBondCashIncome() == null) {
      throw new IllegalArgumentException("Manual bond cash income is required");
    }
  }

  private List<PlanInputWarning> warnings(
      BigDecimal inflation,
      BigDecimal investmentReturn,
      BigDecimal effectiveRental,
      BigDecimal effectiveSpending,
      ExpenseProfile profile,
      int ageAtPlanStart,
      int currentPlanningAge,
      int retirementAge) {
    List<PlanInputWarning> warnings = new ArrayList<>();
    if (inflation.compareTo(new BigDecimal("-0.05")) < 0
        || inflation.compareTo(new BigDecimal("0.30")) > 0)
      warnings.add(
          new PlanInputWarning(
              "inflation",
              "INFLATION_UNUSUAL",
              "Inflation is unusually high or low for a long-term planning assumption. The value will still be used."));
    if (investmentReturn.compareTo(new BigDecimal("-0.20")) < 0
        || investmentReturn.compareTo(new BigDecimal("0.25")) > 0)
      warnings.add(
          new PlanInputWarning(
              "equityReturn",
              "INVESTMENT_RETURN_UNUSUAL",
              "Investment return is unusual. The value will still be used."));
    warnEffective(warnings, "rentalIncomeGrowthSpread", "RENTAL_GROWTH_UNUSUAL", effectiveRental);
    warnEffective(warnings, "spendingGrowthSpread", "SPENDING_GROWTH_UNUSUAL", effectiveSpending);
    for (ExpenseProfileStep step : profile.steps()) {
      int stageAge = ageAtPlanStart + step.fromYear();
      String field = "expenseProfile:" + stageAge;
      if (stageAge < currentPlanningAge)
        warnings.add(
            new PlanInputWarning(
                field,
                "STAGE_BEFORE_CURRENT_AGE",
                "This stage starts before the current planning age and affects historical/current plan years."));
      if (stageAge < retirementAge)
        warnings.add(
            new PlanInputWarning(
                field, "STAGE_BEFORE_RETIREMENT", "This spending stage starts before retirement."));
      if (step.factor().compareTo(new BigDecimal("1.20")) > 0)
        warnings.add(
            new PlanInputWarning(
                field,
                "EXPENSE_LEVEL_HIGH",
                "A spending level above 120% is unusually high for a long-term stage."));
      if (step.factor().compareTo(new BigDecimal("0.50")) < 0)
        warnings.add(
            new PlanInputWarning(
                field,
                "EXPENSE_LEVEL_LOW",
                "A spending level below 50% is unusually low for a long-term stage."));
    }
    return List.copyOf(warnings);
  }

  private static void warnEffective(
      List<PlanInputWarning> warnings, String field, String code, BigDecimal effective) {
    if (effective.compareTo(new BigDecimal("-0.10")) < 0
        || effective.compareTo(new BigDecimal("0.15")) > 0)
      warnings.add(
          new PlanInputWarning(
              field, code, "Effective growth is unusual. The value will still be used."));
  }

  private ExpenseProfile expenseProfile(
      List<PlanEditorInput.ExpenseStageInput> stages,
      ExpenseProfile fallback,
      int ageAtPlanStart,
      int endAge) {
    if (stages == null) return fallback;
    if (stages.isEmpty()) return ExpenseProfile.EMPTY;
    List<ExpenseProfileStep> steps = new ArrayList<>();
    HashSet<Integer> ages = new HashSet<>();
    for (PlanEditorInput.ExpenseStageInput stage : stages) {
      int age = stage.age();
      BigDecimal level = stage.level();
      if (age < ageAtPlanStart)
        throw new IllegalArgumentException(
            "Stage age cannot be before plan-start age " + ageAtPlanStart);
      if (age > endAge)
        throw new IllegalArgumentException("Stage age cannot be after end age " + endAge);
      if (level.signum() < 0 || level.compareTo(new BigDecimal("200")) > 0)
        throw new IllegalArgumentException("Spending level must be between 0% and 200%");
      if (!ages.add(age)) throw new IllegalArgumentException("Duplicate stage age " + age);
      steps.add(new ExpenseProfileStep(age - ageAtPlanStart, fraction(level)));
    }
    steps.sort(java.util.Comparator.comparingInt(ExpenseProfileStep::fromYear));
    return new ExpenseProfile(steps);
  }

  private void validateTimeline(
      int startYear, int ageAtStart, int retirementAge, int endAge, int currentYear) {
    int currentAge = ageAtStart + currentYear - startYear;
    if (startYear > currentYear
        || ageAtStart < 0
        || endAge < currentAge
        || retirementAge < ageAtStart
        || retirementAge > endAge) throw new IllegalArgumentException("Invalid plan timeline");
  }

  private static void validRate(BigDecimal rate, String field) {
    if (rate.compareTo(NEGATIVE_ONE) <= 0) throw new IllegalArgumentException("Invalid " + field);
  }

  private static void nonNegative(BigDecimal value, String field) {
    if (value.signum() < 0) throw new IllegalArgumentException("Invalid " + field);
  }

  private BigDecimal money(BigDecimal value, CurrencyType currency, BigDecimal fallback) {
    return value == null ? fallback : presentation.fromDisplay(value, currency, fallback);
  }

  private static BigDecimal rate(BigDecimal value, BigDecimal fallback) {
    return value == null ? fallback : fraction(value);
  }

  private static BigDecimal fraction(BigDecimal percentagePoints) {
    return percentagePoints.divide(ONE_HUNDRED, PERCENTAGE_MATH_CONTEXT).stripTrailingZeros();
  }

  private static int integer(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private static boolean booleanValue(Boolean value, boolean fallback) {
    return value == null ? fallback : value;
  }

  public record Normalized(SimulationAssumptions assumptions, List<PlanInputWarning> warnings) {}
}
