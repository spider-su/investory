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
    FrozenBondCashFlowProjection bondProjection = new FrozenBondCashFlowProjection();
    BigDecimal baseBondYield = bondProjection.baseCapitalizedBondYield(
        profile, assumptions.fixedIncomeReturnRate());
    BigDecimal bondYield;
    if (bondProjection.hasCapitalizedBondYield(profile)) {
      bondYield = baseBondYield.add(
          selected.fixedIncomeReturnRate().subtract(base.fixedIncomeReturnRate()));
    } else if (bondProjection.hasFrozenBondAssets(profile)) {
      // PAY_OUT-only source Bonds generate spendable cash; their principal must not also receive
      // a scenario capital return. This prevents double-counting the same interest.
      bondYield = BigDecimal.ZERO;
    } else {
      // Allocation-only / synthetic planning state has no source mechanics, so use the plan rate.
      bondYield = selected.fixedIncomeReturnRate();
    }
    return new ScenarioEffectiveAssumptions(
        selected.inflationRate(),
        baseBondYield,
        selected.effectiveRentalIncomeGrowthRate(),
        selected.effectiveSpendingGrowthRate(),
        bondYield,
        selected.equityReturnRate());
  }
}
