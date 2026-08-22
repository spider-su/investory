package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysis;
import com.smartbox.investory.retirement.simulation.SimulationChartData;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis;

/** Derived interpretation over an already prepared retirement projection context. */
public record RetirementAnalysisResult(
    SustainableSpendingAnalysis sustainableSpending,
    RetirementAgeAnalysis retirementAge,
    SimulationSensitivityAnalysis sensitivity,
    SimulationChartData charts) {}
