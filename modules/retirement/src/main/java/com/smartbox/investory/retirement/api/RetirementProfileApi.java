package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.AnnualCostView;
import com.smartbox.investory.shared.currency.CurrencyType;

/** Small retirement-owned read boundary for profile summary data. */
public interface RetirementProfileApi {
  AnnualCostView currentYearAnnualCost(Long portfolioId, CurrencyType reportingCurrency);
}
