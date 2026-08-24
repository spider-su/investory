package com.smartbox.investory.retirement.simulation;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
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

    return profile.longTermAssets().stream()
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
        profile.allocations().stream()
            .filter(allocation -> allocation.bucket() == EconomicBucket.FIXED_INCOME)
            .map(allocation -> zeroIfNull(allocation.value()))
            .reduce(ZERO, BigDecimal::add);
    if (bondCapital.signum() == 0) {
      bondCapital =
          profile.longTermAssets().stream()
              .filter(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME)
              .map(asset -> zeroIfNull(asset.currentValue()))
              .reduce(ZERO, BigDecimal::add);
    }
    BigDecimal capitalizedReturn =
        profile.longTermAssets().stream()
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
    return profile.longTermAssets().stream()
        .filter(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME)
        .filter(asset -> asset.interestTreatment() == InterestTreatmentModel.CAPITALIZE)
        .map(asset -> activePeriodCapitalizedReturn(asset, baselineYear))
        .anyMatch(value -> value.signum() != 0);
  }

  public boolean hasCapitalizedBondYield(InvestmentProfile profile, int firstYear, int lastYear) {
    return profile.longTermAssets().stream()
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

  /** True when the frozen source snapshot contains explicit Bond assets. */
  public boolean hasFrozenBondAssets(InvestmentProfile profile) {
    return profile.longTermAssets().stream()
        .anyMatch(asset -> asset.bucket() == EconomicBucket.FIXED_INCOME);
  }

  private BigDecimal activePeriodCapitalizedReturn(ProjectedLongTermAsset asset, int baselineYear) {
    var period = activePeriod(asset, baselineYear);
    if (period == null) return ZERO;
    BigDecimal declared = zeroIfNull(period.annualIncome());
    if (declared.signum() != 0) return declared;
    return zeroIfNull(asset.currentValue())
        .multiply(zeroIfNull(period.annualReturnRate()))
        .multiply(BigDecimal.ONE.subtract(zeroIfNull(asset.taxRate())));
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

  private static BigDecimal periodCashIncome(ProjectedLongTermAsset asset, int year) {
    var period = activePeriod(asset, year);
    if (period == null) return ZERO;
    BigDecimal declared = zeroIfNull(period.annualIncome());
    if (declared.signum() != 0) return declared;
    return zeroIfNull(asset.currentValue())
        .multiply(zeroIfNull(period.annualReturnRate()))
        .multiply(BigDecimal.ONE.subtract(zeroIfNull(asset.taxRate())));
  }
}
