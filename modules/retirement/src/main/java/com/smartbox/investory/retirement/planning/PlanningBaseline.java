package com.smartbox.investory.retirement.planning;
import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;


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
    reserve = zeroIfNull(reserve);
    investmentCapital = zeroIfNull(investmentCapital);
    longTermCapital = zeroIfNull(longTermCapital);
    rentalAnnualIncome = zeroIfNull(rentalAnnualIncome);
    longTermAnnualIncome = zeroIfNull(longTermAnnualIncome);
    longTermPlanningState = longTermPlanningState == null
        ? LongTermAnnualProjectionApi.PlanningState.EMPTY : longTermPlanningState;
  }

  public static PlanningBaseline fromProfile(InvestmentProfile profile, int asOfYear) {
    // Generic liquidity is not a Retirement funding bucket: ETFs and other liquid securities
    // remain Investment capital. InvestmentProfile supplies the explicit economic decomposition.
    BigDecimal reserve = zeroIfNull(profile.retirementReserve());
    return new PlanningBaseline(asOfYear,
        reserve,
        zeroIfNull(profile.investmentCapital()),
        profile.longTermAssetValue(), profile.currentRentalIncome(), profile.currentBondIncome(),
        profile.longTermPlanningState());
  }

  
}
