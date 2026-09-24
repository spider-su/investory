package com.smartbox.investory.ui.profile;

import com.smartbox.investory.retirement.api.model.AnnualCostView;
import com.smartbox.investory.shared.currency.CurrencyType;

/** UI-side seam that can later be replaced by an HTTP client. */
public interface RetirementProfileClient {
  AnnualCostView currentYearAnnualCost(Long portfolioId, CurrencyType reportingCurrency);
}
