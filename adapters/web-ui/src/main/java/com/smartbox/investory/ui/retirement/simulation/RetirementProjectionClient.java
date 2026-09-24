package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface RetirementProjectionClient {
  RetirementProjection load(Long portfolioId, Long planId);

  RetirementProjection load(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge);

  RetirementProjection project(
      com.smartbox.investory.profile.api.model.InvestmentProfile profile,
      SimulationAssumptions assumptions,
      PlanningBaseline baseline);
}
