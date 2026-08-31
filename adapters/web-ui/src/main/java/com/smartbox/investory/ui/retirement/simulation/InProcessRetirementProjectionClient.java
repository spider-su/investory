package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.RetirementProjectionContext;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationCustomDeltas;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementProjectionClient implements RetirementProjectionClient {
  private final RetirementProjectionApi retirementProjectionApi;

  public InProcessRetirementProjectionClient(
      @Qualifier("retirementProjectionFacade") RetirementProjectionApi retirementProjectionApi) {
    this.retirementProjectionApi = retirementProjectionApi;
  }

  public RetirementProjectionContext load(Long portfolioId, Long planId) {
    return retirementProjectionApi.load(portfolioId, planId);
  }

  public RetirementProjectionContext load(
      Long portfolioId,
      Long planId,
      Integer defaultCurrentAge,
      Integer defaultEndAge,
      SimulationCustomDeltas customDeltas) {
    return retirementProjectionApi.load(
        portfolioId, planId, defaultCurrentAge, defaultEndAge, customDeltas);
  }

  public RetirementProjectionContext project(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      PlanningBaseline baseline,
      SimulationCustomDeltas customDeltas) {
    return retirementProjectionApi.project(profile, assumptions, baseline, customDeltas);
  }
}
