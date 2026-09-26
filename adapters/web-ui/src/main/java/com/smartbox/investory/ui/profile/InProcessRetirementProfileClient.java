package com.smartbox.investory.ui.profile;

import com.smartbox.investory.retirement.api.model.AnnualCostView;
import com.smartbox.investory.retirement.rest.RetirementProfileRestController;
import com.smartbox.investory.shared.currency.CurrencyType;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementProfileClient implements RetirementProfileClient {
  private final RetirementProfileRestController rest;

  public InProcessRetirementProfileClient(RetirementProfileRestController rest) {
    this.rest = rest;
  }

  @Override
  public AnnualCostView currentYearAnnualCost(Long portfolioId, CurrencyType reportingCurrency) {
    return rest.annualCost(portfolioId, reportingCurrency);
  }
}
