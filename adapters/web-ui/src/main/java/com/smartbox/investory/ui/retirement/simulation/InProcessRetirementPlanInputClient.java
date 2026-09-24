package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.NormalizedPlanInput;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.rest.RetirementTimelineContracts.NormalizePlanEditorInputRequest;
import com.smartbox.investory.retirement.rest.RetirementTimelineRestController;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.springframework.stereotype.Component;

/** In-process adapter for retirement plan-editor input normalization. */
@Component
public class InProcessRetirementPlanInputClient implements RetirementPlanInputClient {
  private final RetirementTimelineRestController rest;

  public InProcessRetirementPlanInputClient(RetirementTimelineRestController rest) {
    this.rest = rest;
  }

  public NormalizedPlanInput normalizePlanEditorInput(
      PlanEditorInput input, SimulationAssumptions base, CurrencyType displayCurrency) {
    return rest.normalizePlanEditorInput(
        new NormalizePlanEditorInputRequest(input, base, displayCurrency));
  }
}
