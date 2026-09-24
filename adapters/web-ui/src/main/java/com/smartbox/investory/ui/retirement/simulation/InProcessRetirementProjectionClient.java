package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.contract.RetirementProjectionContracts.DisplayProjectionParameters;
import com.smartbox.investory.retirement.api.contract.RetirementProjectionContracts.DisplayProjectionResponse;
import com.smartbox.investory.retirement.api.contract.RetirementProjectionContracts.ProjectionParameters;
import com.smartbox.investory.retirement.api.contract.RetirementProjectionContracts.ProjectionRequest;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.RetirementProjection;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.rest.RetirementProjectionRestController;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementProjectionClient implements RetirementProjectionClient {
  private final RetirementProjectionRestController rest;

  public InProcessRetirementProjectionClient(RetirementProjectionRestController rest) {
    this.rest = rest;
  }

  public RetirementProjection load(Long portfolioId, Long planId) {
    return rest.load(portfolioId, new ProjectionParameters(planId, null, null));
  }

  public RetirementProjection load(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge) {
    return rest.load(
        portfolioId, new ProjectionParameters(planId, defaultCurrentAge, defaultEndAge));
  }

  public DisplayProjectionResponse loadDisplay(
      Long portfolioId,
      Long planId,
      Integer defaultCurrentAge,
      Integer defaultEndAge,
      CurrencyType displayCurrency) {
    return rest.loadDisplay(
        portfolioId,
        new DisplayProjectionParameters(planId, defaultCurrentAge, defaultEndAge, displayCurrency));
  }

  public RetirementProjection project(
      InvestmentProfile profile, SimulationAssumptions assumptions, PlanningBaseline baseline) {
    return rest.project(new ProjectionRequest(profile, assumptions, baseline));
  }
}
