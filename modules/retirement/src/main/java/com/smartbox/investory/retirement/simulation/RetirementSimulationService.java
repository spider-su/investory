package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
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
    return simulate(profile, assumptions, scenario, baselineYear, SimulationCustomDeltas.zero());
  }

  @Override
  public SimulationResult simulate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      SimulationCustomDeltas custom) {
    return simulateWithCustom(profile, assumptions, scenario, baselineYear, custom);
  }

  private SimulationResult simulateWithCustom(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      SimulationCustomDeltas custom) {

    ScenarioEffectiveAssumptions effective =
        ScenarioEffectiveAssumptions.forScenario(
            profile, assumptions, scenario, baselineYear, custom);
    PlanningBuckets buckets =
        PlanningBuckets.fromProfileWithBondYield(
            profile, effective.equityReturnRate(), effective.capitalBondReturnRate());
    var engine = new RetirementBucketEngine();
    var years = new java.util.ArrayList<SimulationYear>();
    Integer failureAge = null;
    BigDecimal firstShortfall = ZERO, totalUnfunded = ZERO;
    BigDecimal spending =
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses());
    // Profile rental income is the frozen baseline for the explicit baseline year supplied by
    // the forward context. The simulator itself does not resolve calendar time.
    int yearsFromBaseline = Math.max(0, assumptions.startYear() - baselineYear);
    BigDecimal rental =
        buckets
            .rentalCashIncome()
            .multiply(
                BigDecimal.ONE.add(effective.rentalIncomeGrowthRate()).pow(yearsFromBaseline));
    PlanningBuckets current = buckets;
    for (int age = assumptions.currentAge(); age <= assumptions.endAge(); age++) {
      int year = assumptions.startYear() + age - assumptions.currentAge();
      boolean retired = age >= assumptions.retirementAge();
      BigDecimal costs =
          retired ? spending.multiply(assumptions.expenseProfileFactorForCalendarYear(year)) : ZERO;
      BigDecimal employment = retired ? ZERO : assumptions.annualEmploymentIncome();
      BigDecimal pension =
          age >= assumptions.pensionStartAge() ? assumptions.annualPension() : ZERO;
      BigDecimal eventIncome =
          assumptions.futureEvents().stream()
              .filter(e -> e.year() == year && e.type() == SimulationEventType.ONE_OFF_INCOME)
              .map(SimulationEvent::amount)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal eventExpense =
          assumptions.futureEvents().stream()
              .filter(e -> e.year() == year && e.type() == SimulationEventType.ONE_OFF_EXPENSE)
              .map(SimulationEvent::amount)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal bondIncome = bondCashFlows.cashIncome(profile, assumptions, year);
      BigDecimal cashIncome = employment.add(pension).add(eventIncome).add(rental).add(bondIncome);
      BigDecimal annualBondReturn = effective.capitalBondReturnRate();
      BigDecimal annualEquityReturn = effective.equityReturnRate();
      PlanningBuckets annualBuckets =
          new PlanningBuckets(
              current.cash(),
              new PlanningBucket(
                  BucketType.BONDS,
                  current.bonds().startValue(),
                  annualBondReturn,
                  current.bonds().spendingPriority(),
                  current.bonds().targetValue(),
                  RefillPolicy.NONE),
              new PlanningBucket(
                  BucketType.EQUITIES,
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
      var c = result.buckets().get(BucketType.CASH);
      var b = result.buckets().get(BucketType.BONDS);
      var rawEquities = result.buckets().get(BucketType.EQUITIES);
      // Contributions are an input cash flow before the next year, not a source-domain holding.
      var e =
          retired || assumptions.annualPreRetirementContribution().signum() == 0
              ? rawEquities
              : new RetirementBucketEngine.BucketResult(
                  BucketType.EQUITIES,
                  rawEquities.startValue(),
                  rawEquities.returnAmount(),
                  rawEquities.refill(),
                  rawEquities.withdrawal(),
                  rawEquities
                      .expectedEndValue()
                      .add(assumptions.annualPreRetirementContribution()));
      var re = result.buckets().get(BucketType.REAL_ESTATE);
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
              rental,
              cashIncome,
              bondIncome,
              c,
              b,
              e,
              re,
              result.unfunded(),
              retired ? ZERO : assumptions.annualPreRetirementContribution()));
      BigDecimal nextEquities = e.expectedEndValue();
      current =
          new PlanningBuckets(
              new PlanningBucket(
                  BucketType.CASH, c.expectedEndValue(), ZERO, 1, ZERO, RefillPolicy.NONE),
              new PlanningBucket(
                  BucketType.BONDS,
                  b.expectedEndValue(),
                  current.bonds().plannedYieldRate(),
                  2,
                  current.bonds().targetValue(),
                  RefillPolicy.NONE),
              new PlanningBucket(
                  BucketType.EQUITIES,
                  nextEquities,
                  current.equities().plannedYieldRate(),
                  3,
                  ZERO,
                  RefillPolicy.EQUITY_HARVEST),
              new PlanningBucket(
                  BucketType.REAL_ESTATE, re.expectedEndValue(), ZERO, 4, ZERO, RefillPolicy.NONE),
              rental,
              current.realEstateGrowthRate());
      rental = rental.multiply(BigDecimal.ONE.add(effective.rentalIncomeGrowthRate()));
      if (retired) spending = spending.multiply(BigDecimal.ONE.add(effective.spendingGrowthRate()));
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
    return compareScenarios(profile, assumptions, baselineYear, SimulationCustomDeltas.zero());
  }

  @Override
  public Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      int baselineYear,
      SimulationCustomDeltas custom) {
    EnumMap<SimulationScenario, SimulationResult> results = new EnumMap<>(SimulationScenario.class);
    for (SimulationScenario scenario : SimulationScenario.values())
      results.put(
          scenario, simulateWithCustom(profile, assumptions, scenario, baselineYear, custom));
    return results;
  }
}
