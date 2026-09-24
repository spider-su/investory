package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.EditorPreviewResponse;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.shared.currency.CurrencyType;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface RetirementPreviewClient {
  EditorPreviewResponse preview(
      Long portfolioId, Long planId, CurrencyType displayCurrency, PlanEditorInput input);
}
