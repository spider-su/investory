package com.smartbox.investory.retirement.planning.application;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.analysis.*;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.projection.*;
import com.smartbox.investory.retirement.planning.reconciliation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.planning.timeline.*;
import com.smartbox.investory.retirement.preview.*;
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
    // Plans created before the canonical baseline migration may have capital facts but no
    // serialized Long-Term asset state. Keep the current reviewed profile state in that case;
    // otherwise the future simulator loses fixed-income assets while live income still shows them.
    var planningState =
        baseline.longTermPlanningState().assets().isEmpty()
                && !profile.longTermPlanningState().assets().isEmpty()
            ? profile.longTermPlanningState()
            : baseline.longTermPlanningState();
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
        planningState,
        reserve,
        investment,
        profile.incomeSummary(),
        profile.allocationReconciliation());
  }

  private static BigDecimal zero(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }
}
