package com.smartbox.investory.retirement.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import java.math.BigDecimal;

/** Frozen economic starting values used by a reviewed future plan. */
public record PlanningBaseline(
    int asOfYear,
    BigDecimal reserve,
    BigDecimal investmentCapital,
    BigDecimal longTermCapital,
    BigDecimal rentalAnnualIncome,
    BigDecimal longTermAnnualIncome,
    ProfileAssetProjection longTermPlanningState) {
  public PlanningBaseline(
      int asOfYear,
      BigDecimal reserve,
      BigDecimal investmentCapital,
      BigDecimal longTermCapital,
      BigDecimal rentalAnnualIncome,
      BigDecimal longTermAnnualIncome) {
    this(
        asOfYear,
        reserve,
        investmentCapital,
        longTermCapital,
        rentalAnnualIncome,
        longTermAnnualIncome,
        null);
  }

  public PlanningBaseline {
    reserve = zeroIfNull(reserve);
    investmentCapital = zeroIfNull(investmentCapital);
    longTermCapital = zeroIfNull(longTermCapital);
    rentalAnnualIncome = zeroIfNull(rentalAnnualIncome);
    longTermAnnualIncome = zeroIfNull(longTermAnnualIncome);
    longTermPlanningState =
        longTermPlanningState == null ? ProfileAssetProjection.EMPTY : longTermPlanningState;
  }

  public static PlanningBaseline fromProfile(InvestmentProfile profile, int asOfYear) {
    // Generic liquidity is not a Retirement funding bucket: ETFs and other liquid securities
    // remain Investment capital. InvestmentProfile supplies the explicit economic decomposition.
    BigDecimal reserve = zeroIfNull(profile.retirementReserve());
    return new PlanningBaseline(
        asOfYear,
        reserve,
        zeroIfNull(profile.investmentCapital()),
        profile.longTermAssetValue(),
        profile.currentRentalIncome(),
        profile.currentBondIncome(),
        profile.longTermPlanningState());
  }
}
