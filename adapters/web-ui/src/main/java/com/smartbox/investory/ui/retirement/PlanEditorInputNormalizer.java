package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningCurrencyPresentationService;
import com.smartbox.investory.retirement.simulation.ExpenseProfile;
import com.smartbox.investory.retirement.simulation.ExpenseProfileStep;
import com.smartbox.investory.retirement.simulation.ProjectedIncomePolicy;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Converts semantic browser values into canonical simulation assumptions in one place. */
public final class PlanEditorInputNormalizer {
  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal NEGATIVE_ONE = BigDecimal.ONE.negate();
  private final PlanningCurrencyPresentationService presentation;
  private final Clock clock;

  public PlanEditorInputNormalizer(PlanningCurrencyPresentationService presentation, Clock clock) {
    this.presentation = presentation;
    this.clock = clock;
  }

  public Normalized normalize(
      PlanEditorInput input, SimulationAssumptions base, CurrencyType displayCurrency) {
    int ageAtStart = integer(input, "ageAtPlanStart", base.ageAtPlanStart());
    int startYear = integer(input, "startYear", base.planStartYear());
    int endAge = integer(input, "endAge", base.endAge());
    int retirementAge = integer(input, "retirementAge", base.retirementAge());
    validateTimeline(startYear, ageAtStart, retirementAge, endAge);

    BigDecimal inflation = rate(input, "inflation", base.inflationRate());
    BigDecimal rentalSpread =
        rate(input, "rentalIncomeGrowthSpread", base.rentalIncomeGrowthSpread());
    BigDecimal spendingSpread = rate(input, "spendingGrowthSpread", base.spendingGrowthSpread());
    BigDecimal bondReturn = rate(input, "fixedIncomeReturn", base.fixedIncomeReturnRate());
    BigDecimal investmentReturn = rate(input, "equityReturn", base.equityReturnRate());
    BigDecimal effectiveRental = inflation.add(rentalSpread);
    BigDecimal effectiveSpending = inflation.add(spendingSpread);
    BigDecimal reserveYears = decimal(input, "safeReserveYears");
    BigDecimal harvestThreshold =
        rate(input, "equityHarvestThreshold", base.equityHarvestMinimumReturnRate());
    BigDecimal harvestShare = rate(input, "equityHarvestShare", base.equityGainHarvestRate());
    if (reserveYears == null) reserveYears = base.safeReserveYears();
    if (harvestShare == null) harvestShare = base.equityGainHarvestRate();
    if (reserveYears.signum() < 0)
      throw new IllegalArgumentException("Reserve target years cannot be negative");
    if (harvestShare.signum() < 0 || harvestShare.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Harvest share must be between 0 and 100%");
    validRate(inflation, "inflation");
    validRate(bondReturn, "fixedIncomeReturn");
    validRate(investmentReturn, "equityReturn");
    validRate(effectiveRental, "rentalIncomeGrowthSpread");
    validRate(effectiveSpending, "spendingGrowthSpread");

    BigDecimal monthly = money(input, "monthlyLivingCosts", displayCurrency, null);
    BigDecimal annualLiving =
        monthly == null ? base.annualLivingExpenses() : monthly.multiply(BigDecimal.valueOf(12));
    BigDecimal extras =
        money(input, "discretionaryExpenses", displayCurrency, base.annualDiscretionaryExpenses());
    BigDecimal employment =
        money(input, "annualEmploymentIncome", displayCurrency, base.annualEmploymentIncome());
    BigDecimal contribution =
        money(
            input,
            "annualPreRetirementContribution",
            displayCurrency,
            base.annualPreRetirementContribution());
    BigDecimal pension = money(input, "annualPension", displayCurrency, base.annualPension());
    nonNegative(annualLiving, "monthlyLivingCosts");
    nonNegative(extras, "discretionaryExpenses");
    nonNegative(employment, "annualEmploymentIncome");
    nonNegative(contribution, "annualPreRetirementContribution");
    nonNegative(pension, "annualPension");

    SimulationAssumptions assumptions =
        new SimulationAssumptions(
                ageAtStart,
                endAge,
                annualLiving,
                inflation,
                base.cashReturnRate(),
                bondReturn,
                investmentReturn,
                base.realEstateReturnRate(),
                base.otherReturnRate(),
                integer(input, "pensionStartAge", base.pensionStartAge()),
                pension,
                base.capitalGainTaxRate(),
                startYear,
                extras,
                base.futureEvents(),
                rentalSpread,
                spendingSpread,
                base.fundingStrategy(),
                reserveYears,
                harvestThreshold,
                harvestShare,
                booleanValue(
                    input, "allowEmergencyEquityWithdrawal", base.allowEmergencyEquityWithdrawal()),
                retirementAge,
                employment,
                contribution)
            .withFundingOrder(base.fundingOrder())
            .withExpenseProfile(
                expenseProfile(
                    input.value("expenseProfile"), base.expenseProfile(), ageAtStart, endAge));
    ProjectedIncomePolicy incomePolicy =
        new ProjectedIncomePolicy(
            mode(input.value("rentalIncomeMode"), base.projectedIncomePolicy().rentalIncomeMode()),
            money(
                input,
                "manualRentalIncome",
                displayCurrency,
                base.projectedIncomePolicy().manualRentalIncome()),
            mode(
                input.value("bondCashIncomeMode"),
                base.projectedIncomePolicy().bondCashIncomeMode()),
            money(
                input,
                "manualBondCashIncome",
                displayCurrency,
                base.projectedIncomePolicy().manualBondCashIncome()));
    return new Normalized(
        assumptions.withProjectedIncomePolicy(incomePolicy),
        warnings(
            inflation,
            investmentReturn,
            effectiveRental,
            effectiveSpending,
            assumptions.expenseProfile(),
            ageAtStart,
            ageAtStart + Year.now(clock).getValue() - startYear,
            retirementAge));
  }

  private static ProjectedIncomePolicy.IncomeMode mode(
      String value, ProjectedIncomePolicy.IncomeMode fallback) {
    if (value == null || value.isBlank()) return fallback;
    return ProjectedIncomePolicy.IncomeMode.valueOf(
        value.trim().toUpperCase(java.util.Locale.ROOT));
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
      String raw, ExpenseProfile fallback, int ageAtPlanStart, int endAge) {
    if (raw == null) return fallback;
    if (raw.isBlank()) return ExpenseProfile.EMPTY;
    List<ExpenseProfileStep> steps = new ArrayList<>();
    HashSet<Integer> ages = new HashSet<>();
    for (String entry : raw.split(";")) {
      String[] values = entry.split(":", -1);
      if (values.length != 2) throw new IllegalArgumentException("Invalid expense profile");
      int age = stageAge(values[0]);
      BigDecimal level = new BigDecimal(values[1].trim());
      if (age < ageAtPlanStart)
        throw new IllegalArgumentException(
            "Stage age cannot be before plan-start age " + ageAtPlanStart);
      if (age > endAge)
        throw new IllegalArgumentException("Stage age cannot be after end age " + endAge);
      if (level.signum() < 0 || level.compareTo(new BigDecimal("200")) > 0)
        throw new IllegalArgumentException("Spending level must be between 0% and 200%");
      if (!ages.add(age)) throw new IllegalArgumentException("Duplicate stage age " + age);
      steps.add(new ExpenseProfileStep(age - ageAtPlanStart, level.divide(ONE_HUNDRED)));
    }
    steps.sort(java.util.Comparator.comparingInt(ExpenseProfileStep::fromYear));
    return new ExpenseProfile(steps);
  }

  private static int stageAge(String value) {
    try {
      return new BigDecimal(value.trim()).intValueExact();
    } catch (NumberFormatException | ArithmeticException exception) {
      throw new IllegalArgumentException("Stage age must be an integer", exception);
    }
  }

  private void validateTimeline(int startYear, int ageAtStart, int retirementAge, int endAge) {
    int currentAge = ageAtStart + Year.now(clock).getValue() - startYear;
    if (startYear > Year.now(clock).getValue()
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

  private BigDecimal money(
      PlanEditorInput input, String field, CurrencyType currency, BigDecimal fallback) {
    BigDecimal value = decimal(input, field);
    return value == null ? fallback : presentation.fromDisplay(value, currency, fallback);
  }

  private static BigDecimal rate(PlanEditorInput input, String field, BigDecimal fallback) {
    BigDecimal value = decimal(input, field);
    return value == null ? fallback : value.divide(ONE_HUNDRED);
  }

  private static int integer(PlanEditorInput input, String field, int fallback) {
    BigDecimal value = decimal(input, field);
    return value == null ? fallback : value.intValueExact();
  }

  private static boolean booleanValue(PlanEditorInput input, String field, boolean fallback) {
    String raw = input.value(field);
    return raw == null || raw.isBlank() ? fallback : Boolean.parseBoolean(raw);
  }

  private static BigDecimal decimal(PlanEditorInput input, String field) {
    String raw = input.value(field);
    return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
  }

  public record Normalized(SimulationAssumptions assumptions, List<PlanInputWarning> warnings) {}
}
