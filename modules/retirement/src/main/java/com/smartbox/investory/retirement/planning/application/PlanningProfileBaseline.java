package com.smartbox.investory.retirement.planning.application;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    // Legacy snapshots can lack Long-Term assets or bucket classifications. Resolve those against
    // the current source state so frozen totals remain usable and the simulator never drops an
    // unclassified bucket from its starting capital.
    var planningState = resolvePlanningState(profile, baseline.longTermPlanningState());
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

  private static ProfileAssetProjection resolvePlanningState(
      InvestmentProfile profile, ProfileAssetProjection frozen) {
    var current = profile.longTermPlanningState();
    if (frozen.assets().isEmpty() && !current.assets().isEmpty()) return current;
    if (frozen.assets().stream().noneMatch(asset -> asset.bucket() == null)) return frozen;

    Map<Long, ProjectedLongTermAsset> currentById =
        current.assets().stream()
            .filter(asset -> asset.id() != null)
            .collect(
                Collectors.toMap(
                    ProjectedLongTermAsset::id, Function.identity(), (left, right) -> left));
    var repaired =
        frozen.assets().stream()
            .map(
                asset -> {
                  var source = currentById.get(asset.id());
                  if (asset.bucket() != null || source == null) return asset;
                  return new ProjectedLongTermAsset(
                      asset.id(),
                      asset.name(),
                      source.bucket(),
                      asset.currency(),
                      asset.currentValue(),
                      source.liquidity(),
                      source.periods(),
                      source.rentalContracts(),
                      source.maturityDate());
                })
            .toList();
    if (repaired.stream().anyMatch(asset -> asset.bucket() == null)) return current;
    return new ProfileAssetProjection(
        repaired, frozen.rentalIncomeGrowthRate(), frozen.rentalIncomeBaseYear(), frozen.source());
  }

  private static BigDecimal zero(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }
}
