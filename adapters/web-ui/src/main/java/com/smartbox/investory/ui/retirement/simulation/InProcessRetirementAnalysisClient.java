package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.RetirementAnalysisResult;
import com.smartbox.investory.retirement.rest.RetirementAnalysisRestController;
import com.smartbox.investory.retirement.rest.RetirementProjectionContracts.ProjectionParameters;
import com.smartbox.investory.ui.retirement.analysis.RetirementAnalysisClient;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementAnalysisClient implements RetirementAnalysisClient {
  private final RetirementAnalysisRestController rest;

  public InProcessRetirementAnalysisClient(RetirementAnalysisRestController rest) {
    this.rest = rest;
  }

  public RetirementAnalysisResult analyze(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge) {
    return rest.analyze(
            portfolioId, new ProjectionParameters(planId, defaultCurrentAge, defaultEndAge))
        .toDomain();
  }
}
