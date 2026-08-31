package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPreviewApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanEditorPreview;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementPreviewClient implements RetirementPreviewClient {
  private final RetirementPreviewApi retirementPreviewApi;

  public InProcessRetirementPreviewClient(
      @Qualifier("retirementPreviewApplicationService") RetirementPreviewApi retirementPreviewApi) {
    this.retirementPreviewApi = retirementPreviewApi;
  }

  public PlanEditorPreview preview(
      InvestmentProfile profile, SimulationAssumptions assumptions, CurrencyType displayCurrency) {
    return retirementPreviewApi.preview(profile, assumptions, displayCurrency);
  }
}
