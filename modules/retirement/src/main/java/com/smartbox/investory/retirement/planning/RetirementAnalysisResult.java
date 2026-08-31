package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.AnalysisAvailability;
import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysis;
import com.smartbox.investory.retirement.simulation.SimulationChartData;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis;
import java.util.Objects;

/** Derived interpretation over an already prepared retirement projection context. */
public record RetirementAnalysisResult(
    RetirementAnalysisState state,
    AnalysisAvailability<SustainableSpendingAnalysis> sustainableSpending,
    AnalysisAvailability<RetirementAgeAnalysis> retirementAge,
    AnalysisAvailability<SimulationSensitivityAnalysis> sensitivity,
    SimulationChartData charts) {
  public RetirementAnalysisResult {
    Objects.requireNonNull(state, "Analysis state is required");
    Objects.requireNonNull(charts, "Analysis charts are required");
    if (state == RetirementAnalysisState.AVAILABLE) {
      if (!sustainableSpending.available()
          || !retirementAge.available()
          || !sensitivity.available())
        throw new IllegalArgumentException("Available Analysis requires all derived values");
    }
  }

  public boolean available() {
    return state == RetirementAnalysisState.AVAILABLE;
  }

  public static RetirementAnalysisResult noForwardHorizon(SimulationChartData charts) {
    return new RetirementAnalysisResult(
        RetirementAnalysisState.NO_FORWARD_HORIZON,
        new AnalysisAvailability.Unavailable<>("No forward horizon"),
        new AnalysisAvailability.Unavailable<>("No forward horizon"),
        new AnalysisAvailability.Unavailable<>("No forward horizon"),
        charts);
  }
}
