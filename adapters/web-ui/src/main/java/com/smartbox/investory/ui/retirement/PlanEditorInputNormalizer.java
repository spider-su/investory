package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningCurrencyPresentationService;
import com.smartbox.investory.retirement.simulation.ExpenseProfile;
import com.smartbox.investory.retirement.simulation.ExpenseProfileStep;
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
    BigDecimal rentalSpread = rate(input, "rentalIncomeGrowthSpread", base.rentalIncomeGrowthSpread());
    BigDecimal spendingSpread = rate(input, "spendingGrowthSpread", base.spendingGrowthSpread());
    BigDecimal investmentReturn = rate(input, "equityReturn", base.equityReturnRate());
    BigDecimal effectiveRental = inflation.add(rentalSpread);
    BigDecimal effectiveSpending = inflation.add(spendingSpread);
    validRate(inflation, "inflation");
    validRate(investmentReturn, "equityReturn");
    validRate(effectiveRental, "rentalIncomeGrowthSpread");
    validRate(effectiveSpending, "spendingGrowthSpread");

    BigDecimal monthly = money(input, "monthlyLivingCosts", displayCurrency, null);
    BigDecimal annualLiving =
        monthly == null ? base.annualLivingExpenses() : monthly.multiply(BigDecimal.valueOf(12));
    BigDecimal extras = money(input, "discretionaryExpenses", displayCurrency, base.annualDiscretionaryExpenses());
    BigDecimal employment = money(input, "annualEmploymentIncome", displayCurrency, base.annualEmploymentIncome());
    BigDecimal contribution = money(input, "annualPreRetirementContribution", displayCurrency, base.annualPreRetirementContribution());
    BigDecimal pension = money(input, "annualPension", displayCurrency, base.annualPension());
    nonNegative(annualLiving, "monthlyLivingCosts");
    nonNegative(extras, "discretionaryExpenses");
    nonNegative(employment, "annualEmploymentIncome");
    nonNegative(contribution, "annualPreRetirementContribution");
    nonNegative(pension, "annualPension");

    SimulationAssumptions assumptions =
        new SimulationAssumptions(
                ageAtStart, endAge, annualLiving, inflation, base.cashReturnRate(),
                base.fixedIncomeReturnRate(), investmentReturn, base.realEstateReturnRate(),
                base.otherReturnRate(), integer(input, "pensionStartAge", base.pensionStartAge()),
                pension, base.capitalGainTaxRate(), startYear, extras, base.futureEvents(),
                rentalSpread, spendingSpread, base.fundingStrategy(), base.safeReserveYears(),
                base.equityHarvestMinimumReturnRate(), base.equityGainHarvestRate(),
                base.allowEmergencyEquityWithdrawal(), retirementAge, employment, contribution)
            .withFundingOrder(base.fundingOrder())
            .withExpenseProfile(expenseProfile(input.value("expenseProfile"), base.expenseProfile()));
    return new Normalized(assumptions, warnings(inflation, investmentReturn, effectiveRental, effectiveSpending, assumptions.expenseProfile()));
  }

  private List<PlanInputWarning> warnings(
      BigDecimal inflation, BigDecimal investmentReturn, BigDecimal effectiveRental,
      BigDecimal effectiveSpending, ExpenseProfile profile) {
    List<PlanInputWarning> warnings = new ArrayList<>();
    if (inflation.compareTo(new BigDecimal("-0.05")) < 0 || inflation.compareTo(new BigDecimal("0.30")) > 0)
      warnings.add(new PlanInputWarning("inflation", "INFLATION_UNUSUAL", "Inflation is unusually high or low for a long-term planning assumption. The value will still be used."));
    if (investmentReturn.compareTo(new BigDecimal("-0.20")) < 0 || investmentReturn.compareTo(new BigDecimal("0.25")) > 0)
      warnings.add(new PlanInputWarning("equityReturn", "INVESTMENT_RETURN_UNUSUAL", "Investment return is unusual. The value will still be used."));
    warnEffective(warnings, "rentalIncomeGrowthSpread", "RENTAL_GROWTH_UNUSUAL", effectiveRental);
    warnEffective(warnings, "spendingGrowthSpread", "SPENDING_GROWTH_UNUSUAL", effectiveSpending);
    if (profile.steps().stream().anyMatch(step -> step.factor().compareTo(new BigDecimal("1.20")) > 0))
      warnings.add(new PlanInputWarning("expenseProfile", "EXPENSE_LEVEL_UNUSUAL", "A spending level above 120% is unusual. The value will still be used."));
    return List.copyOf(warnings);
  }

  private static void warnEffective(List<PlanInputWarning> warnings, String field, String code, BigDecimal effective) {
    if (effective.compareTo(new BigDecimal("-0.10")) < 0 || effective.compareTo(new BigDecimal("0.15")) > 0)
      warnings.add(new PlanInputWarning(field, code, "Effective growth is unusual. The value will still be used."));
  }

  private ExpenseProfile expenseProfile(String raw, ExpenseProfile fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    List<ExpenseProfileStep> steps = new ArrayList<>();
    HashSet<Integer> ages = new HashSet<>();
    for (String entry : raw.split(";")) {
      String[] values = entry.split(":", -1);
      if (values.length != 2) throw new IllegalArgumentException("Invalid expense profile");
      int age = Integer.parseInt(values[0].trim());
      BigDecimal level = new BigDecimal(values[1].trim());
      if (age < 0 || level.signum() < 0 || level.compareTo(new BigDecimal("200")) > 0 || !ages.add(age))
        throw new IllegalArgumentException("Invalid expense profile");
      steps.add(new ExpenseProfileStep(age, level.divide(ONE_HUNDRED)));
    }
    return new ExpenseProfile(steps);
  }

  private void validateTimeline(int startYear, int ageAtStart, int retirementAge, int endAge) {
    int currentAge = ageAtStart + Year.now(clock).getValue() - startYear;
    if (startYear > Year.now(clock).getValue() || ageAtStart < 0 || endAge < currentAge
        || retirementAge < ageAtStart || retirementAge > endAge)
      throw new IllegalArgumentException("Invalid plan timeline");
  }

  private static void validRate(BigDecimal rate, String field) {
    if (rate.compareTo(NEGATIVE_ONE) <= 0) throw new IllegalArgumentException("Invalid " + field);
  }

  private static void nonNegative(BigDecimal value, String field) {
    if (value.signum() < 0) throw new IllegalArgumentException("Invalid " + field);
  }

  private BigDecimal money(PlanEditorInput input, String field, CurrencyType currency, BigDecimal fallback) {
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

  private static BigDecimal decimal(PlanEditorInput input, String field) {
    String raw = input.value(field);
    return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
  }

  public record Normalized(SimulationAssumptions assumptions, List<PlanInputWarning> warnings) {}
}
