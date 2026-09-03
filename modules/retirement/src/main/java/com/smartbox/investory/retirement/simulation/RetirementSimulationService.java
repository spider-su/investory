package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.FrozenBondCashFlowProjection;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Canonical retirement orchestrator. Asset mechanics remain behind public module APIs. */
@Service
public class RetirementSimulationService implements RetirementSimulation {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final FrozenBondCashFlowProjection bondCashFlows;

  public RetirementSimulationService() {
    this(new FrozenBondCashFlowProjection());
  }

  @Autowired
  public RetirementSimulationService(FrozenBondCashFlowProjection bondCashFlows) {
    this.bondCashFlows = bondCashFlows;
  }

  @Override
  public SimulationResult simulate(
      InvestmentProfile profile, SimulationAssumptions assumptions, SimulationScenario scenario) {
    return simulate(profile, assumptions, scenario, assumptions.startYear());
  }

  @Override
  public SimulationResult simulate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear) {
    return simulateInternal(profile, assumptions, scenario, baselineYear, BigDecimal.ONE, false);
  }

  @Override
  public SimulationYear simulateRemainingYear(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      BigDecimal recurringFraction) {
    if (recurringFraction == null
        || recurringFraction.signum() < 0
        || recurringFraction.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("Recurring fraction must be between 0 and 1");
    }
    return simulateInternal(profile, assumptions, scenario, baselineYear, recurringFraction, true)
        .years()
        .getFirst();
  }

  private SimulationResult simulateInternal(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      BigDecimal firstYearRecurringFraction,
      boolean firstYearOnly) {

    ScenarioEffectiveAssumptions effective =
        ScenarioEffectiveAssumptions.forScenario(profile, assumptions, scenario, baselineYear);
    PlanningBuckets buckets =
        PlanningBuckets.fromProfileWithBondYield(
            profile, effective.equityReturnRate(), effective.capitalBondReturnRate());
    var engine = new RetirementBucketEngine();
    var years = new java.util.ArrayList<SimulationYear>();
    Integer failureAge = null;
    BigDecimal firstShortfall = ZERO, totalUnfunded = ZERO;
    BigDecimal spending = assumptions.annualSpending();
    // Profile rental income is the frozen baseline for the explicit baseline year supplied by
    // the forward context. The simulator itself does not resolve calendar time.
    int yearsFromBaseline = Math.max(0, assumptions.startYear() - baselineYear);
    BigDecimal rentalBaseline = buckets.rentalCashIncome();
    BigDecimal rental =
        rentalBaseline.multiply(
            BigDecimal.ONE.add(effective.rentalIncomeGrowthRate()).pow(yearsFromBaseline));
    Map<Integer, BigDecimal> eventIncomeByYear =
        eventTotals(assumptions, SimulationEventType.ONE_OFF_INCOME);
    Map<Integer, BigDecimal> eventExpenseByYear =
        eventTotals(assumptions, SimulationEventType.ONE_OFF_EXPENSE);
    PlanningBuckets current = buckets;
    for (int age = assumptions.currentAge(); age <= assumptions.endAge(); age++) {
      int year = assumptions.startYear() + age - assumptions.currentAge();
      BigDecimal recurringFraction =
          age == assumptions.currentAge() ? firstYearRecurringFraction : BigDecimal.ONE;
      boolean retired = age >= assumptions.retirementAge();
      BigDecimal costs =
          retired
              ? spending
                  .multiply(assumptions.expenseProfileFactorForCalendarYear(year))
                  .multiply(recurringFraction)
              : ZERO;
      BigDecimal employment =
          retired ? ZERO : assumptions.annualEmploymentIncome().multiply(recurringFraction);
      BigDecimal pension =
          assumptions.pensionStartsAtOrBefore(age)
              ? assumptions.annualPension().multiply(recurringFraction)
              : ZERO;
      BigDecimal eventIncome = eventIncomeByYear.getOrDefault(year, ZERO);
      BigDecimal eventExpense = eventExpenseByYear.getOrDefault(year, ZERO);
      BigDecimal periodRental = rental.multiply(recurringFraction);
      BigDecimal bondIncome =
          bondCashFlows.cashIncome(profile, assumptions, year).multiply(recurringFraction);
      BigDecimal cashIncome =
          employment.add(pension).add(eventIncome).add(periodRental).add(bondIncome);
      BigDecimal annualBondReturn = effective.capitalBondReturnRate().multiply(recurringFraction);
      BigDecimal annualEquityReturn = effective.equityReturnRate().multiply(recurringFraction);
      PlanningBuckets annualBuckets =
          new PlanningBuckets(
              current.cash(),
              new PlanningBucket(
                  EconomicBucket.FIXED_INCOME,
                  current.bonds().startValue(),
                  annualBondReturn,
                  current.bonds().spendingPriority(),
                  current.bonds().targetValue(),
                  RefillPolicy.NONE),
              new PlanningBucket(
                  EconomicBucket.EQUITY,
                  current.equities().startValue(),
                  annualEquityReturn,
                  current.equities().spendingPriority(),
                  current.equities().targetValue(),
                  RefillPolicy.EQUITY_HARVEST),
              current.realEstate(),
              current.rentalCashIncome(),
              current.realEstateGrowthRate());
      var result =
          engine.simulate(
              annualBuckets,
              costs.add(eventExpense),
              cashIncome,
              assumptions.fundingPolicy(),
              annualBondReturn,
              annualEquityReturn);
      var c = result.buckets().get(EconomicBucket.LIQUID_CASH);
      var b = result.buckets().get(EconomicBucket.FIXED_INCOME);
      var rawEquities = result.buckets().get(EconomicBucket.EQUITY);
      // Contributions are an input cash flow before the next year, not a source-domain holding.
      BigDecimal contribution =
          retired
              ? ZERO
              : assumptions.annualPreRetirementContribution().multiply(recurringFraction);
      var e =
          retired || contribution.signum() == 0
              ? rawEquities
              : new BucketResult(
                  EconomicBucket.EQUITY,
                  rawEquities.startValue(),
                  rawEquities.returnAmount(),
                  rawEquities.refill(),
                  rawEquities.withdrawal(),
                  rawEquities.expectedEndValue().add(contribution));
      var re = result.buckets().get(EconomicBucket.REAL_ESTATE);
      if (result.unfunded().signum() > 0 && failureAge == null) {
        failureAge = age;
        firstShortfall = result.unfunded();
      }
      totalUnfunded = totalUnfunded.add(result.unfunded());
      years.add(
          SimulationYear.bucket(
              age,
              year,
              retired,
              costs,
              eventExpense,
              employment,
              pension,
              eventIncome,
              periodRental,
              cashIncome,
              bondIncome,
              c,
              b,
              e,
              re,
              result.unfunded(),
              contribution));
      BigDecimal nextEquities = e.expectedEndValue();
      current =
          new PlanningBuckets(
              new PlanningBucket(
                  EconomicBucket.LIQUID_CASH,
                  c.expectedEndValue(),
                  ZERO,
                  1,
                  ZERO,
                  RefillPolicy.NONE),
              new PlanningBucket(
                  EconomicBucket.FIXED_INCOME,
                  b.expectedEndValue(),
                  current.bonds().plannedYieldRate(),
                  2,
                  current.bonds().targetValue(),
                  RefillPolicy.NONE),
              new PlanningBucket(
                  EconomicBucket.EQUITY,
                  nextEquities,
                  current.equities().plannedYieldRate(),
                  3,
                  ZERO,
                  RefillPolicy.EQUITY_HARVEST),
              new PlanningBucket(
                  EconomicBucket.REAL_ESTATE,
                  re.expectedEndValue(),
                  ZERO,
                  4,
                  ZERO,
                  RefillPolicy.NONE),
              rental,
              current.realEstateGrowthRate());
      rental = rental.multiply(BigDecimal.ONE.add(effective.rentalIncomeGrowthRate()));
      if (retired) spending = spending.multiply(BigDecimal.ONE.add(effective.spendingGrowthRate()));
      if (firstYearOnly) break;
    }
    return new SimulationResult(
        scenario, failureAge != null, failureAge, firstShortfall, totalUnfunded, years);
  }

  @Override
  public Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    return compareScenarios(profile, assumptions, assumptions.startYear());
  }

  @Override
  public Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile, SimulationAssumptions assumptions, int baselineYear) {
    EnumMap<SimulationScenario, SimulationResult> results = new EnumMap<>(SimulationScenario.class);
    for (SimulationScenario scenario : SimulationScenario.values())
      results.put(
          scenario,
          simulateInternal(profile, assumptions, scenario, baselineYear, BigDecimal.ONE, false));
    return results;
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  private static Map<Integer, BigDecimal> eventTotals(
      SimulationAssumptions assumptions, SimulationEventType type) {
    return assumptions.futureEvents().stream()
        .filter(event -> event.type() == type)
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                SimulationEvent::year, SimulationEvent::amount, BigDecimal::add));
  }
}
