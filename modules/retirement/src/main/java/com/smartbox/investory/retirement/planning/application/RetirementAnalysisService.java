package com.smartbox.investory.retirement.planning.application;

import com.smartbox.investory.retirement.analysis.*;
import com.smartbox.investory.retirement.analysis.DeterministicAnalysisContext;
import com.smartbox.investory.retirement.analysis.RetirementAgeAnalysisService;
import com.smartbox.investory.retirement.analysis.SimulationSensitivityAnalysisService;
import com.smartbox.investory.retirement.analysis.SustainableSpendingAnalysisService;
import com.smartbox.investory.retirement.api.RetirementAnalysisApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.AnalysisAvailability;
import com.smartbox.investory.retirement.api.model.PlanSustainabilityAssessment;
import com.smartbox.investory.retirement.api.model.SimulationChartData;
import com.smartbox.investory.retirement.api.model.SimulationEvaluation;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.projection.*;
import com.smartbox.investory.retirement.planning.reconciliation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.planning.timeline.*;
import com.smartbox.investory.retirement.preview.*;
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

  public RetirementAnalysisResult analyze(RetirementProjection projection) {
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
        new AnalysisAvailability.Available<>(sustainableSpending.analyze(context)),
        new AnalysisAvailability.Available<>(retirementAge.analyze(context)),
        new AnalysisAvailability.Available<>(sensitivity.analyze(context)),
        charts);
  }
}
