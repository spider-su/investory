package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanEditorPreview;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface RetirementPreviewClient {
  PlanEditorPreview preview(
      InvestmentProfile profile, SimulationAssumptions assumptions, CurrencyType displayCurrency);
}
