package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi.AnnualCosts;
import java.math.BigDecimal;

/** UI-side client for plan-owned annual-cost groups. */
public interface AnnualCostsClient {
  AnnualCosts load(Long portfolioId, Long planId, int year);

  AnnualCosts saveGroup(
      Long portfolioId, Long planId, int year, Long groupId, String name, BigDecimal monthlyAmount);

  AnnualCosts deleteGroup(Long portfolioId, Long planId, int year, Long groupId);

  AnnualCosts saveAnnualExtras(Long portfolioId, Long planId, int year, BigDecimal annualAmount);
}
