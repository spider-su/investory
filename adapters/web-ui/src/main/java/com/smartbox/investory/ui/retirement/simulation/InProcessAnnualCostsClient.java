package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi;
import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi.AnnualCosts;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class InProcessAnnualCostsClient implements AnnualCostsClient {
  private final RetirementAnnualCostsApi annualCosts;

  public InProcessAnnualCostsClient(RetirementAnnualCostsApi annualCosts) {
    this.annualCosts = annualCosts;
  }

  @Override
  public AnnualCosts load(Long portfolioId, Long planId, int year) {
    return annualCosts.load(portfolioId, planId, year);
  }

  @Override
  public AnnualCosts saveGroup(
      Long portfolioId,
      Long planId,
      int year,
      Long groupId,
      String name,
      BigDecimal monthlyAmount) {
    return annualCosts.saveGroup(portfolioId, planId, year, groupId, name, monthlyAmount);
  }

  @Override
  public AnnualCosts deleteGroup(Long portfolioId, Long planId, int year, Long groupId) {
    return annualCosts.deleteGroup(portfolioId, planId, year, groupId);
  }

  @Override
  public AnnualCosts saveAnnualExtras(
      Long portfolioId, Long planId, int year, BigDecimal annualAmount) {
    return annualCosts.saveAnnualExtras(portfolioId, planId, year, annualAmount);
  }
}
