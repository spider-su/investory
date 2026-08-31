package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.api.RetirementAnalysisApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.AnalysisAvailability;
import com.smartbox.investory.retirement.api.model.PlanSustainabilityAssessment;
import com.smartbox.investory.retirement.api.model.SimulationChartData;
import com.smartbox.investory.retirement.api.model.SimulationEvaluation;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.simulation.DeterministicAnalysisContext;
import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysisService;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysisService;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysisService;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Orchestrates derived retirement analysis without rebuilding the base projection pipeline. */
@Service
public class RetirementAnalysisService implements RetirementAnalysisApi {
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
        SimulationChartData.from(analysisScenarios(projection), projection.projectedAssumptions());
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
        new AnalysisAvailability.Available<>(sustainableSpending.analyze(context)),
        new AnalysisAvailability.Available<>(retirementAge.analyze(context)),
        new AnalysisAvailability.Available<>(sensitivity.analyze(context)),
        charts);
  }

  /** Analysis hides the engine's zero-delta Custom compatibility result. */
  private static Map<
          SimulationScenario, com.smartbox.investory.retirement.api.model.SimulationResult>
      analysisScenarios(RetirementProjectionContext projection) {
    Map<SimulationScenario, com.smartbox.investory.retirement.api.model.SimulationResult> result =
        new EnumMap<>(SimulationScenario.class);
    result.putAll(projection.scenarioResults());
    var base = result.get(SimulationScenario.BASE);
    var custom = result.get(SimulationScenario.CUSTOM);
    if (base != null && custom != null && base.years().equals(custom.years())) {
      result.remove(SimulationScenario.CUSTOM);
    }
    return result;
  }
}
