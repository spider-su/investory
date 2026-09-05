package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import java.math.BigDecimal;

/** Single compatibility boundary from the compact sandbox request to plan assumptions. */
public final class RetirementSandboxInputTranslator {
  private RetirementSandboxInputTranslator() {}

  public static SimulationAssumptions toAssumptions(SandboxSimulationInput input) {
    return SimulationAssumptions.defaults(input.currentAge(), input.endAge(), input.startYear())
        .toBuilder()
        .retirementAge(input.retirementAge())
        .annualLivingExpenses(input.annualSpending())
        .inflationRate(input.inflationRate())
        .fixedIncomeReturnRate(input.bondReturnRate())
        .equityReturnRate(input.equityReturnRate())
        .pensionStartAge(Math.max(input.retirementAge(), input.pensionAge()))
        .annualPension(input.monthlyPensionIncome().multiply(BigDecimal.valueOf(12)))
        // The sandbox has no rental-growth control; preserve its flat nominal rental input.
        .rentalIncomeGrowthSpread(input.inflationRate().negate())
        .spendingGrowthSpread(BigDecimal.ZERO)
        .build();
  }
}
