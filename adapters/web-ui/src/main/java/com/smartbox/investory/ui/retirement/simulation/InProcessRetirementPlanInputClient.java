package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementPlanInputApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.NormalizedPlanInput;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** In-process adapter for retirement plan-editor input normalization. */
@Component
public class InProcessRetirementPlanInputClient implements RetirementPlanInputClient {
  private final RetirementPlanInputApi api;

  public InProcessRetirementPlanInputClient(
      @Qualifier("retirementPlanningApplicationService") RetirementPlanInputApi api) {
    this.api = api;
  }

  public NormalizedPlanInput normalizePlanEditorInput(
      PlanEditorInput input, SimulationAssumptions base, CurrencyType displayCurrency) {
    return api.normalizePlanEditorInput(input, base, displayCurrency);
  }
}
