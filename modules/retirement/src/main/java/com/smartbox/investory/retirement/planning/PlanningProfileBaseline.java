package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;

/** Applies a reviewed Retirement baseline without adding planning behavior to Profile. */
public final class PlanningProfileBaseline {
  private PlanningProfileBaseline() {}

  public static InvestmentProfile apply(
      InvestmentProfile profile,
      BigDecimal reserve,
      BigDecimal investmentCapital,
      BigDecimal longTermCapital,
      BigDecimal rentalAnnualIncome,
      BigDecimal longTermAnnualIncome) {
    return apply(
        profile,
        new PlanningBaseline(
            profile.longTermPlanningState().rentalIncomeBaseYear(),
            reserve,
            investmentCapital,
            longTermCapital,
            rentalAnnualIncome,
            longTermAnnualIncome,
            profile.longTermPlanningState()));
  }

  public static InvestmentProfile apply(
      InvestmentProfile profile,
      BigDecimal reserve,
      BigDecimal investmentCapital,
      BigDecimal longTermCapital,
      BigDecimal rentalAnnualIncome,
      BigDecimal longTermAnnualIncome,
      com.smartbox.investory.profile.api.model.ProfileAssetProjection planningState) {
    return apply(
        profile,
        new PlanningBaseline(
            planningState.rentalIncomeBaseYear(),
            reserve,
            investmentCapital,
            longTermCapital,
            rentalAnnualIncome,
            longTermAnnualIncome,
            planningState));
  }

  public static InvestmentProfile apply(InvestmentProfile profile, PlanningBaseline baseline) {
    BigDecimal reserve = zero(baseline.reserve());
    BigDecimal investment = zero(baseline.investmentCapital());
    BigDecimal longTerm = zero(baseline.longTermCapital());
    return new InvestmentProfile(
        profile.portfolioId(),
        profile.currency(),
        reserve.add(investment),
        longTerm,
        reserve.add(investment).add(longTerm),
        reserve,
        profile.illiquidAssets(),
        profile.allocations(),
        baseline.rentalAnnualIncome(),
        baseline.longTermAnnualIncome(),
        baseline.longTermPlanningState(),
        reserve,
        investment,
        profile.incomeSummary(),
        profile.allocationReconciliation());
  }

  private static BigDecimal zero(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }
}
