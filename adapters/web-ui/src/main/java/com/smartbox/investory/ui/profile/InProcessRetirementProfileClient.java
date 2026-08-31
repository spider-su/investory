package com.smartbox.investory.ui.profile;

import com.smartbox.investory.retirement.api.RetirementProfileApi;
import com.smartbox.investory.retirement.api.model.AnnualCostView;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementProfileClient implements RetirementProfileClient {
  private final RetirementProfileApi retirementProfileApi;

  public InProcessRetirementProfileClient(
      @Qualifier("retirementProfileApplicationService") RetirementProfileApi retirementProfileApi) {
    this.retirementProfileApi = retirementProfileApi;
  }

  @Override
  public AnnualCostView currentYearAnnualCost(Long portfolioId, CurrencyType reportingCurrency) {
    return retirementProfileApi.currentYearAnnualCost(portfolioId, reportingCurrency);
  }
}
