package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import java.math.BigDecimal;
import java.time.Year;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Canonical retirement orchestrator. Asset mechanics remain behind public module APIs. */
@Service
public class RetirementSimulationService implements RetirementSimulation {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final LongTermAnnualProjectionApi longTerm;
  private final InvestmentAnnualProjectionApi investments;

  @Autowired
  public RetirementSimulationService(LongTermAnnualProjectionApi longTerm,
      InvestmentAnnualProjectionApi investments) {
    this.longTerm = longTerm;
    this.investments = investments;
  }

  @Override
  public SimulationResult simulate(InvestmentProfile profile, SimulationAssumptions assumptions,
      SimulationScenario scenario) { return simulate(profile, assumptions, scenario, false); }

  @Override
  public SimulationResult simulate(InvestmentProfile profile, SimulationAssumptions assumptions,
      SimulationScenario scenario, boolean actualRentalYear) {
    SimulationScenarioSettings settings = SimulationScenarioSettings.forScenario(scenario, assumptions);
    PlanningBuckets buckets = PlanningBuckets.fromProfile(profile, settings.equityReturnRate(),
        assumptions.fixedIncomeReturnRate());
    var engine = new RetirementBucketEngine();
    var years = new java.util.ArrayList<SimulationYear>();
    Integer failureAge = null; BigDecimal firstShortfall = ZERO, totalUnfunded = ZERO;
    BigDecimal spending = assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses());
    // Profile rental income is the frozen current-year baseline. Forward contexts are rebased
    // to the first projected year, so advance that baseline before emitting the first row.
    int baselineYear = Year.now().getValue();
    int yearsFromBaseline = Math.max(0, assumptions.startYear() - baselineYear);
    BigDecimal rental = buckets.rentalCashIncome().multiply(
        BigDecimal.ONE.add(settings.effectiveRentalIncomeGrowthRate()).pow(yearsFromBaseline));
    PlanningBuckets current = buckets;
    for (int age = assumptions.currentAge(); age <= assumptions.endAge(); age++) {
      int year = assumptions.startYear() + age - assumptions.currentAge();
      boolean retired = age >= assumptions.retirementAge();
      BigDecimal costs = retired ? spending.multiply(assumptions.expenseProfileFactorForCalendarYear(year)) : ZERO;
      BigDecimal employment = retired ? ZERO : assumptions.annualEmploymentIncome();
      BigDecimal pension = age >= assumptions.pensionStartAge() ? assumptions.annualPension() : ZERO;
      BigDecimal eventIncome = assumptions.futureEvents().stream().filter(e -> e.year() == year && e.type() == SimulationEventType.ONE_OFF_INCOME).map(SimulationEvent::amount).reduce(ZERO, BigDecimal::add);
      BigDecimal eventExpense = assumptions.futureEvents().stream().filter(e -> e.year() == year && e.type() == SimulationEventType.ONE_OFF_EXPENSE).map(SimulationEvent::amount).reduce(ZERO, BigDecimal::add);
      BigDecimal cashIncome = employment.add(pension).add(eventIncome).add(rental);
      var result = engine.simulate(current, costs.add(eventExpense), cashIncome, assumptions.fundingPolicy());
      var c = result.buckets().get(BucketType.CASH); var b = result.buckets().get(BucketType.BONDS);
      var rawEquities = result.buckets().get(BucketType.EQUITIES);
      // Contributions are an input cash flow before the next year, not a source-domain holding.
      var e = retired || assumptions.annualPreRetirementContribution().signum() == 0 ? rawEquities
          : new RetirementBucketEngine.BucketResult(BucketType.EQUITIES, rawEquities.startValue(), rawEquities.returnAmount(),
              rawEquities.refill(), rawEquities.withdrawal(), rawEquities.expectedEndValue().add(assumptions.annualPreRetirementContribution()));
      var re = result.buckets().get(BucketType.REAL_ESTATE);
      if (result.unfunded().signum() > 0 && failureAge == null) { failureAge = age; firstShortfall = result.unfunded(); }
      totalUnfunded = totalUnfunded.add(result.unfunded());
      years.add(SimulationYear.bucket(age, year, retired, costs, eventExpense, employment, pension, eventIncome,
          rental, cashIncome, c, b, e, re, result.unfunded(), retired ? ZERO : assumptions.annualPreRetirementContribution()));
      BigDecimal nextEquities = e.expectedEndValue();
      current = new PlanningBuckets(
          new PlanningBucket(BucketType.CASH, c.expectedEndValue(), ZERO, 1, ZERO, RefillPolicy.NONE),
          new PlanningBucket(BucketType.BONDS, b.expectedEndValue(), current.bonds().plannedYieldRate(), 2, current.bonds().targetValue(), RefillPolicy.NONE),
          new PlanningBucket(BucketType.EQUITIES, nextEquities, current.equities().plannedYieldRate(), 3, ZERO, RefillPolicy.EQUITY_HARVEST),
          new PlanningBucket(BucketType.REAL_ESTATE, re.expectedEndValue(), ZERO, 4, ZERO, RefillPolicy.NONE),
          rental, current.realEstateGrowthRate());
      rental = rental.multiply(BigDecimal.ONE.add(settings.effectiveRentalIncomeGrowthRate()));
      if (retired) spending = spending.multiply(BigDecimal.ONE.add(settings.effectiveSpendingGrowthRate()));
    }
    return new SimulationResult(scenario, failureAge != null, failureAge, firstShortfall, totalUnfunded, years);
  }

  @Override
  public Map<SimulationScenario, SimulationResult> compareScenarios(InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    EnumMap<SimulationScenario, SimulationResult> results = new EnumMap<>(SimulationScenario.class);
    for (SimulationScenario scenario : SimulationScenario.values()) results.put(scenario, simulate(profile, assumptions, scenario));
    return results;
  }
  private static BigDecimal nz(BigDecimal value) { return value == null ? ZERO : value; }

}
