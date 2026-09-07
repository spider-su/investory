package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.RetirementProjection;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementProjectionClient implements RetirementProjectionClient {
  private final RetirementProjectionApi retirementProjectionApi;

  public InProcessRetirementProjectionClient(
      @Qualifier("retirementProjectionService") RetirementProjectionApi retirementProjectionApi) {
    this.retirementProjectionApi = retirementProjectionApi;
  }

  public RetirementProjection load(Long portfolioId, Long planId) {
    return retirementProjectionApi.load(portfolioId, planId);
  }

  public RetirementProjection load(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge) {
    return retirementProjectionApi.load(portfolioId, planId, defaultCurrentAge, defaultEndAge);
  }

  public RetirementProjection project(
      InvestmentProfile profile, SimulationAssumptions assumptions, PlanningBaseline baseline) {
    return retirementProjectionApi.project(profile, assumptions, baseline);
  }
}
