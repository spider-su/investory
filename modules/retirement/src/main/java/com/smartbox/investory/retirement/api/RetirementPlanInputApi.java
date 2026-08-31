package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.NormalizedPlanInput;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;

/** Public boundary for normalizing retirement plan-editor input. */
public interface RetirementPlanInputApi {
  NormalizedPlanInput normalizePlanEditorInput(
      PlanEditorInput input, SimulationAssumptions base, CurrencyType displayCurrency);
}
