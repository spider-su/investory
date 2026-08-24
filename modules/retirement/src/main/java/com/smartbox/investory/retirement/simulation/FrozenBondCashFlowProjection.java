package com.smartbox.investory.retirement.simulation;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Evaluates bond cash-flow details from the immutable profile planning snapshot. */
@Service
public class FrozenBondCashFlowProjection {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public BigDecimal cashIncome(
      InvestmentProfile profile, SimulationAssumptions assumptions, int year) {
    var policy = assumptions.projectedIncomePolicy();
    if (policy.bondCashIncomeMode() == ProjectedIncomePolicy.IncomeMode.MANUAL)
      return zeroIfNull(policy.manualBondCashIncome());

    return frozenAssets(profile).stream()
        .filter(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME)
        .filter(asset -> asset.maturityDate() == null || year <= asset.maturityDate().getYear())
        .filter(asset -> asset.interestTreatment() != InterestTreatmentModel.CAPITALIZE)
        .map(asset -> periodCashIncome(asset, year))
        .reduce(ZERO, BigDecimal::add);
  }

  /** Returns the reviewed capitalized-return yield, or the explicit planning fallback. */
  public BigDecimal baseCapitalizedBondYield(
      InvestmentProfile profile, BigDecimal fallbackBondYield, int baselineYear) {
    BigDecimal bondCapital =
        frozenAssets(profile).stream()
            .filter(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME)
            .map(asset -> zeroIfNull(asset.currentValue()))
            .reduce(ZERO, BigDecimal::add);
    BigDecimal capitalizedReturn =
        frozenAssets(profile).stream()
            .filter(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME)
            .filter(asset -> asset.interestTreatment() == InterestTreatmentModel.CAPITALIZE)
            .map(asset -> activePeriodCapitalizedReturn(asset, baselineYear))
            .reduce(ZERO, BigDecimal::add);
    if (bondCapital.signum() == 0) return zeroIfNull(fallbackBondYield);
    if (hasFrozenBondAssets(profile) && capitalizedReturn.signum() == 0) return ZERO;
    return capitalizedReturn.signum() == 0
        ? zeroIfNull(fallbackBondYield)
        : capitalizedReturn.divide(bondCapital, 12, RoundingMode.HALF_UP);
  }

  public boolean hasCapitalizedBondYield(InvestmentProfile profile, int baselineYear) {
    return frozenAssets(profile).stream()
        .filter(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME)
        .filter(asset -> asset.interestTreatment() == InterestTreatmentModel.CAPITALIZE)
        .map(asset -> activePeriodCapitalizedReturn(asset, baselineYear))
        .anyMatch(value -> value.signum() != 0);
  }

  public boolean hasCapitalizedBondYield(InvestmentProfile profile, int firstYear, int lastYear) {
    return frozenAssets(profile).stream()
        .filter(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME)
        .filter(asset -> asset.interestTreatment() == InterestTreatmentModel.CAPITALIZE)
        .anyMatch(
            asset -> {
              for (int year = firstYear; year <= lastYear; year++) {
                if (activePeriodCapitalizedReturn(asset, year).signum() != 0) return true;
              }
              return false;
            });
  }

  /**
   * True when the plan Bond return assumption changes capital during at least one forward year.
   * Reviewed source Bonds are applicable only for active CAPITALIZE periods; PAY_OUT source Bonds
   * contribute cash income and are never made applicable by their balance alone.
   */
  public boolean hasPlanBondReturnExposure(InvestmentProfile profile, int firstYear, int lastYear) {
    if (firstYear > lastYear) return false;
    if (hasFrozenBondAssets(profile)) {
      return hasCapitalizedBondYield(profile, firstYear, lastYear);
    }
    return profile.allocations().stream()
        .anyMatch(a -> a.bucket() == EconomicBucket.FIXED_INCOME && a.isNonZero());
  }

  /** True when the frozen source snapshot contains explicit Bond assets. */
  public boolean hasFrozenBondAssets(InvestmentProfile profile) {
    return frozenAssets(profile).stream()
        .anyMatch(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME);
  }

  private BigDecimal activePeriodCapitalizedReturn(ProjectedLongTermAsset asset, int baselineYear) {
    var period = activePeriod(asset, baselineYear);
    if (period == null) return ZERO;
    return zeroIfNull(period.annualIncome());
  }

  private static ProjectedLongTermAsset.Period activePeriod(
      ProjectedLongTermAsset asset, int year) {
    LocalDate date = LocalDate.of(year, 12, 31);
    return asset.periods().stream()
        .filter(period -> period.validFrom() == null || !date.isBefore(period.validFrom()))
        .filter(period -> period.validTo() == null || !date.isAfter(period.validTo()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Long-Term owns normalization. The profile planning state is the only source used by forward
   * simulation, so live edits cannot change a reviewed revision.
   */
  private static java.util.List<ProjectedLongTermAsset> frozenAssets(InvestmentProfile profile) {
    return profile.longTermPlanningState().assets().stream()
        .map(FrozenBondCashFlowProjection::toRetirementAsset)
        .toList();
  }

  private static ProjectedLongTermAsset toRetirementAsset(
      com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel asset) {
    return new ProjectedLongTermAsset(
        asset.id(),
        asset.name(),
        asset.type(),
        asset.type() == com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel.REAL_ESTATE
            ? EconomicBucket.REAL_ESTATE
            : asset.type() == com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel.BOND
                    || asset.type()
                        == com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel.DEPOSIT
                ? EconomicBucket.FIXED_INCOME
                : EconomicBucket.LIQUID_CASH,
        null,
        asset.currentValue(),
        asset.type() == com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel.REAL_ESTATE
            ? Liquidity.ILLIQUID
            : Liquidity.LIQUID,
        asset.periods().stream()
            .map(
                p ->
                    new ProjectedLongTermAsset.Period(
                        p.validFrom(),
                        p.validTo(),
                        p.annualIncome(),
                        p.annualExpense(),
                        p.annualReturnRate(),
                        p.cashFlowType(),
                        p.paidByTenant()))
            .toList(),
        asset.rentalContracts(),
        asset.maturityDate(),
        asset.redemptionValue(),
        asset.interestTreatment(),
        asset.taxRate(),
        asset.taxBase(),
        asset.rentalTaxPaidByTenant());
  }

  private static BigDecimal periodCashIncome(ProjectedLongTermAsset asset, int year) {
    var period = activePeriod(asset, year);
    if (period == null) return ZERO;
    return zeroIfNull(period.annualIncome());
  }
}
