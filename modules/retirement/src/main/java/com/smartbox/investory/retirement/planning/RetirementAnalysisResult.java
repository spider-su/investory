package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysis;
import com.smartbox.investory.retirement.simulation.SimulationChartData;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis;
import java.util.Objects;

/** Derived interpretation over an already prepared retirement projection context. */
public record RetirementAnalysisResult(
    RetirementAnalysisState state,
    SustainableSpendingAnalysis sustainableSpending,
    RetirementAgeAnalysis retirementAge,
    SimulationSensitivityAnalysis sensitivity,
    SimulationChartData charts) {
  public RetirementAnalysisResult {
    Objects.requireNonNull(state, "Analysis state is required");
    Objects.requireNonNull(charts, "Analysis charts are required");
    if (state == RetirementAnalysisState.AVAILABLE) {
      Objects.requireNonNull(sustainableSpending, "Spending analysis is required");
      Objects.requireNonNull(retirementAge, "Retirement-age analysis is required");
      Objects.requireNonNull(sensitivity, "Sensitivity analysis is required");
    }
  }

  public boolean available() {
    return state == RetirementAnalysisState.AVAILABLE;
  }

  public static RetirementAnalysisResult noForwardHorizon(SimulationChartData charts) {
    return new RetirementAnalysisResult(
        RetirementAnalysisState.NO_FORWARD_HORIZON, null, null, null, charts);
  }
}
