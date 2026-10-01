package com.smartbox.investory.retirement.api;

import java.math.BigDecimal;
import java.util.List;

/** Editable annual-cost breakdown owned by a saved retirement plan. */
public interface RetirementAnnualCostsApi {
  AnnualCosts load(Long portfolioId, Long planId, int year);

  AnnualCosts saveGroup(
      Long portfolioId, Long planId, int year, Long groupId, String name, BigDecimal monthlyAmount);

  AnnualCosts deleteGroup(Long portfolioId, Long planId, int year, Long groupId);

  AnnualCosts saveAnnualExtras(Long portfolioId, Long planId, int year, BigDecimal annualAmount);

  record AnnualCosts(
      Long planId,
      int year,
      String planName,
      List<Group> groups,
      BigDecimal monthlyLivingCosts,
      BigDecimal annualLivingCosts,
      BigDecimal annualExtras,
      Integer approvedYear,
      BigDecimal approvedMonthlyLivingCosts,
      BigDecimal otherMonthlyCosts) {
    public AnnualCosts {
      groups = groups == null ? List.of() : List.copyOf(groups);
    }
  }

  record Group(Long id, String name, BigDecimal monthlyAmount, int sortOrder) {}
}
