package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.contract.RetirementProjectionContracts.DisplayProjectionResponse;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface RetirementProjectionClient {
  RetirementProjection load(Long portfolioId, Long planId);

  RetirementProjection load(
      Long portfolioId, Long planId, Integer defaultCurrentAge, Integer defaultEndAge);

  DisplayProjectionResponse loadDisplay(
      Long portfolioId,
      Long planId,
      Integer defaultCurrentAge,
      Integer defaultEndAge,
      CurrencyType displayCurrency);

  RetirementProjection project(
      com.smartbox.investory.profile.api.model.InvestmentProfile profile,
      SimulationAssumptions assumptions,
      PlanningBaseline baseline);
}
