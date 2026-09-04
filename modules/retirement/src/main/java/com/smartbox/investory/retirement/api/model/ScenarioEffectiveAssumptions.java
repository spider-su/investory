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
    FrozenBondCashFlowProjection bondProjection = new FrozenBondCashFlowProjection();
    BigDecimal capitalBondReturnRate;
    if (bondProjection.hasFrozenBondAssets(profile)
        && !bondProjection.hasCapitalizedBondYield(profile, baselineYear)) {
      // PAY_OUT-only source Bonds generate spendable cash; their principal must not also receive
      // a scenario capital return. This prevents double-counting the same interest.
      capitalBondReturnRate = BigDecimal.ZERO;
    } else {
      // CAPITALIZE and allocation-only planning use the explicit plan assumption plus scenario
      // delta. Source yields remain observed/current data and never replace the plan value.
      capitalBondReturnRate = selected.fixedIncomeReturnRate();
    }
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
