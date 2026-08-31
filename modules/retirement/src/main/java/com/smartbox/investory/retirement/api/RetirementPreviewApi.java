package com.smartbox.investory.retirement.api;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.EditorPreviewResponse;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.api.model.PlanEditorPreview;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;

/** Public application boundary for retirement plan previews. */
public interface RetirementPreviewApi {
  PlanEditorPreview preview(
      InvestmentProfile profile, SimulationAssumptions assumptions, CurrencyType displayCurrency);

  EditorPreviewResponse editorPreview(
      Long portfolioId, Long planId, CurrencyType planningDisplayCurrency, PlanEditorInput input);
}
