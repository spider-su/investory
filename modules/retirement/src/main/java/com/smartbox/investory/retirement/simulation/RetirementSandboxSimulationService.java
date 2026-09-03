package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.retirement.api.RetirementSandboxApi;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

/** Runs the simple portfolio-independent retirement what-if calculation. */
@Service
public final class RetirementSandboxSimulationService implements RetirementSandboxApi {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  @Override
  public SimulationResult simulate(SandboxSimulationInput input) {
    PlanningBuckets current =
        PlanningBuckets.of(
            input.cash(),
            input.bonds(),
            input.equities(),
            ZERO,
            input.bondReturnRate(),
            input.equityReturnRate(),
            input.bonds(),
            ZERO);
    var assumptions =
        SimulationAssumptions.defaults(input.currentAge(), input.endAge(), input.startYear())
            .withRetirementAge(input.retirementAge())
            .withRecurringSpending(input.annualSpending())
            .withInflationRate(input.inflationRate())
            .withSpendingGrowthSpread(ZERO)
            .withFixedIncomeReturnRate(input.bondReturnRate())
            .withEquityReturnRate(input.equityReturnRate());
    var engine = new RetirementBucketEngine();
    var years = new ArrayList<SimulationYear>();
    Integer failureAge = null;
    BigDecimal firstShortfall = ZERO;
    BigDecimal totalUnfunded = ZERO;
    BigDecimal spending = input.annualSpending();

    for (int age = input.currentAge(); age <= input.endAge(); age++) {
      int year = input.startYear() + age - input.currentAge();
      boolean retired = age >= input.retirementAge();
      BigDecimal costs = retired ? spending : ZERO;
      BigDecimal rentalIncome =
          retired ? input.monthlyRentalIncome().multiply(BigDecimal.valueOf(12)) : ZERO;
      BigDecimal pensionIncome =
          retired && age >= input.pensionAge()
              ? input.monthlyPensionIncome().multiply(BigDecimal.valueOf(12))
              : ZERO;
      BigDecimal cashIncome = rentalIncome.add(pensionIncome);
      var result =
          engine.simulate(
              current,
              costs,
              cashIncome,
              RetirementFundingPolicy.defaults(),
              input.bondReturnRate(),
              input.equityReturnRate());
      BucketResult cash = result.buckets().get(EconomicBucket.LIQUID_CASH);
      BucketResult bonds = result.buckets().get(EconomicBucket.FIXED_INCOME);
      BucketResult equities = result.buckets().get(EconomicBucket.EQUITY);
      BucketResult realEstate = result.buckets().get(EconomicBucket.REAL_ESTATE);
      if (result.unfunded().signum() > 0 && failureAge == null) {
        failureAge = age;
        firstShortfall = result.unfunded();
      }
      totalUnfunded = totalUnfunded.add(result.unfunded());
      if (retired) {
        years.add(
            SimulationYear.bucket(
                age,
                year,
                true,
                costs,
                ZERO,
                ZERO,
                pensionIncome,
                ZERO,
                rentalIncome,
                cashIncome,
                ZERO,
                cash,
                bonds,
                equities,
                realEstate,
                result.unfunded(),
                ZERO));
      }
      current =
          PlanningBuckets.of(
              cash.expectedEndValue(),
              bonds.expectedEndValue(),
              equities.expectedEndValue(),
              ZERO,
              input.bondReturnRate(),
              input.equityReturnRate(),
              input.bonds(),
              ZERO);
      if (retired)
        spending = spending.multiply(BigDecimal.ONE.add(assumptions.effectiveSpendingGrowthRate()));
    }
    return new SimulationResult(
        SimulationScenario.BASE,
        failureAge != null,
        failureAge,
        firstShortfall,
        totalUnfunded,
        years);
  }
}
