package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import java.math.BigDecimal;

/** Frozen economic starting values used by a reviewed future plan. */
public record PlanningBaseline(
    int asOfYear,
    BigDecimal reserve,
    BigDecimal investmentCapital,
    BigDecimal longTermCapital,
    BigDecimal rentalAnnualIncome,
    BigDecimal longTermAnnualIncome,
    LongTermAnnualProjectionApi.PlanningState longTermPlanningState) {
  public PlanningBaseline(int asOfYear, BigDecimal reserve, BigDecimal investmentCapital,
      BigDecimal longTermCapital, BigDecimal rentalAnnualIncome, BigDecimal longTermAnnualIncome) {
    this(asOfYear, reserve, investmentCapital, longTermCapital, rentalAnnualIncome,
        longTermAnnualIncome, null);
  }
  public PlanningBaseline {
    reserve = nz(reserve);
    investmentCapital = nz(investmentCapital);
    longTermCapital = nz(longTermCapital);
    rentalAnnualIncome = nz(rentalAnnualIncome);
    longTermAnnualIncome = nz(longTermAnnualIncome);
    longTermPlanningState = longTermPlanningState == null
        ? LongTermAnnualProjectionApi.PlanningState.EMPTY : longTermPlanningState;
  }

  public static PlanningBaseline fromProfile(InvestmentProfile profile, int asOfYear) {
    BigDecimal reserve = nz(profile.liquidAssets());
    return new PlanningBaseline(asOfYear,
        reserve,
        nz(profile.marketPortfolioValue()).subtract(reserve).max(BigDecimal.ZERO),
        profile.longTermAssetValue(), profile.currentRentalIncome(), profile.currentBondIncome(),
        profile.longTermPlanningState());
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
