package com.smartbox.investory.retirement.api;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.RetirementProjection;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;

/** Public query boundary for prepared retirement projections. */
public interface RetirementProjectionApi {
  RetirementProjection load(Long portfolioId, Long planId);

  RetirementProjection load(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge);

  RetirementProjection project(
      InvestmentProfile profile, SimulationAssumptions assumptions, PlanningBaseline baseline);
}
