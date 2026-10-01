package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import java.math.BigDecimal;

/** Scenario overlay applied to the frozen planning baseline for projected years. */
public record ScenarioEffectiveAssumptions(
    BigDecimal inflationRate,
    BigDecimal planBondReturnRate,
    BigDecimal rentalIncomeGrowthRate,
    BigDecimal spendingGrowthRate,
    BigDecimal bondReturnRate,
    BigDecimal capitalBondReturnRate,
    BigDecimal equityReturnRate) {
  public static ScenarioEffectiveAssumptions forScenario(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear) {
    SimulationScenarioSettings selected =
        SimulationScenarioSettings.forScenario(scenario, assumptions);
    // Bond cash income is a spendable cash flow; the plan return applies separately to the
    // defensive capital bucket. Keep this consistent for frozen assets and allocation-only plans.
    BigDecimal capitalBondReturnRate = selected.fixedIncomeReturnRate();
    return new ScenarioEffectiveAssumptions(
        selected.inflationRate(),
        assumptions.fixedIncomeReturnRate(),
        selected.effectiveRentalIncomeGrowthRate(),
        selected.effectiveSpendingGrowthRate(),
        selected.fixedIncomeReturnRate(),
        capitalBondReturnRate,
        selected.equityReturnRate());
  }
}
