package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import java.math.BigDecimal;
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
    PlanningBuckets buckets = frozenBuckets(profile, assumptions, settings);
    var engine = new RetirementBucketEngine();
    var years = new java.util.ArrayList<SimulationYear>();
    Integer failureAge = null; BigDecimal firstShortfall = ZERO, totalUnfunded = ZERO;
    BigDecimal spending = assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses());
    BigDecimal rental = buckets.rentalCashIncome();
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

  private static PlanningBuckets frozenBuckets(InvestmentProfile profile, SimulationAssumptions assumptions,
      SimulationScenarioSettings settings) {
    BigDecimal bonds = allocation(profile, EconomicBucket.FIXED_INCOME);
    if (bonds.signum() == 0) bonds = profile.longTermAssets().stream()
        .filter(a -> a.bucket() == EconomicBucket.FIXED_INCOME).map(a -> nz(a.currentValue())).reduce(ZERO, BigDecimal::add);
    BigDecimal equities = allocation(profile, EconomicBucket.EQUITY);
    if (equities.signum() == 0) equities = nz(profile.investmentCapital());
    BigDecimal realEstate = allocation(profile, EconomicBucket.REAL_ESTATE);
    if (realEstate.signum() == 0) realEstate = profile.longTermAssets().stream()
        .filter(a -> a.bucket() == EconomicBucket.REAL_ESTATE).map(a -> nz(a.currentValue())).reduce(ZERO, BigDecimal::add);
    BigDecimal bondIncome = nz(profile.currentBondIncome());
    if (bondIncome.signum() == 0) bondIncome = profile.longTermAssets().stream()
        .filter(a -> a.bucket() == EconomicBucket.FIXED_INCOME)
        .map(a -> a.currentValue().multiply(a.periods().isEmpty() ? BigDecimal.ZERO : nz(a.periods().getFirst().annualReturnRate()))
            .multiply(BigDecimal.ONE.subtract(nz(a.taxRate())))).reduce(ZERO, BigDecimal::add);
    BigDecimal bondYield = bonds.signum() == 0 ? assumptions.fixedIncomeReturnRate()
        : bondIncome.divide(bonds, 12, java.math.RoundingMode.HALF_UP);
    return PlanningBuckets.of(nz(profile.retirementReserve()), bonds, equities, realEstate,
        bondYield, settings.equityReturnRate(), bonds, profile.currentRentalIncome());
  }

  private static BigDecimal allocation(InvestmentProfile profile, EconomicBucket bucket) {
    return profile.allocations().stream().filter(a -> a.bucket() == bucket).map(a -> nz(a.value())).reduce(ZERO, BigDecimal::add);
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
