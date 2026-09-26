package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.EditorPreviewResponse;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.retirement.rest.RetirementPreviewRestController;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementPreviewClient implements RetirementPreviewClient {
  private final RetirementPreviewRestController rest;

  public InProcessRetirementPreviewClient(RetirementPreviewRestController rest) {
    this.rest = rest;
  }

  public EditorPreviewResponse preview(
      Long portfolioId, Long planId, CurrencyType displayCurrency, PlanEditorInput input) {
    return rest.editorPreview(portfolioId, planId, displayCurrency, input);
  }
}
