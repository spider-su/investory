package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysisService;
import com.smartbox.investory.retirement.simulation.SimulationChartData;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysisService;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysisService;
import org.springframework.stereotype.Service;

/** Orchestrates derived retirement analysis without rebuilding the base projection pipeline. */
@Service
public class RetirementAnalysisService {
  private final SustainableSpendingAnalysisService sustainableSpending;
  private final SimulationSensitivityAnalysisService sensitivity;
  private final RetirementAgeAnalysisService retirementAge;

  public RetirementAnalysisService(
      SustainableSpendingAnalysisService sustainableSpending,
      SimulationSensitivityAnalysisService sensitivity,
      RetirementAgeAnalysisService retirementAge) {
    this.sustainableSpending = sustainableSpending;
    this.sensitivity = sensitivity;
    this.retirementAge = retirementAge;
  }

  public RetirementAnalysisResult analyze(RetirementProjectionContext projection) {
    return new RetirementAnalysisResult(
        sustainableSpending.analyze(projection.projectedProfile(), projection.projectedAssumptions()),
        retirementAge.analyze(projection.projectedProfile(), projection.projectedAssumptions()),
        sensitivity.analyze(projection.projectedProfile(), projection.projectedAssumptions()),
        SimulationChartData.from(projection.scenarioResults(), projection.projectedAssumptions()));
  }
}
