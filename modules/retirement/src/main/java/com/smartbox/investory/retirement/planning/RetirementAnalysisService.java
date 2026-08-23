package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.DeterministicAnalysisContext;
import com.smartbox.investory.retirement.simulation.PlanSustainabilityAssessment;
import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysisService;
import com.smartbox.investory.retirement.simulation.SimulationChartData;
import com.smartbox.investory.retirement.simulation.SimulationEvaluation;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
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
    SimulationChartData charts =
        SimulationChartData.from(projection.scenarioResults(), projection.projectedAssumptions());
    if (projection.forward().forwardAssumptions().isEmpty())
      return RetirementAnalysisResult.noForwardHorizon(charts);

    var baseResult = projection.scenarioResults().get(SimulationScenario.BASE);
    var baseSummary = projection.summaries().get(SimulationScenario.BASE);
    if (baseResult == null || baseSummary == null)
      throw new IllegalStateException("Forward Analysis requires a canonical Base result");
    var context =
        new DeterministicAnalysisContext(
            projection.projectedProfile(),
            projection.projectedAssumptions(),
            projection.forward().context().asOfYear(),
            new SimulationEvaluation(
                baseResult, baseSummary, PlanSustainabilityAssessment.from(baseSummary)));
    return new RetirementAnalysisResult(
        RetirementAnalysisState.AVAILABLE,
        sustainableSpending.analyze(context),
        retirementAge.analyze(context),
        sensitivity.analyze(context),
        charts);
  }
}
