package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;

/** Scenario overlay applied to the frozen planning baseline for projected years. */
public record ScenarioEffectiveAssumptions(
    BigDecimal inflationRate,
    BigDecimal planBondReturnRate,
    BigDecimal rentalIncomeGrowthRate,
    BigDecimal spendingGrowthRate,
    BigDecimal bondReturnRate,
    BigDecimal equityReturnRate) {
  public static ScenarioEffectiveAssumptions forScenario(
      InvestmentProfile profile, SimulationAssumptions assumptions, SimulationScenario scenario) {
    SimulationScenarioSettings selected = SimulationScenarioSettings.forScenario(scenario, assumptions);
    SimulationScenarioSettings base = SimulationScenarioSettings.forScenario(
        SimulationScenario.BASE, assumptions);
    BigDecimal baseBondYield = PlanningBuckets.baseBondYield(
        profile, assumptions.fixedIncomeReturnRate());
    BigDecimal bondYield = PlanningBuckets.hasSourceBondYield(profile)
        ? baseBondYield.add(selected.fixedIncomeReturnRate().subtract(base.fixedIncomeReturnRate()))
        : selected.fixedIncomeReturnRate();
    return new ScenarioEffectiveAssumptions(
        selected.inflationRate(),
        baseBondYield,
        selected.effectiveRentalIncomeGrowthRate(),
        selected.effectiveSpendingGrowthRate(),
        bondYield,
        selected.equityReturnRate());
  }
}
