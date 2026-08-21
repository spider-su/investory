package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Canonical deterministic mechanics for bonds, deposits, and other contractual fixed-income assets.
 * Both the full-year simulator and the current-year bridge use this component.
 */
public final class ContractualAssetProjector {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private ContractualAssetProjector() {}

  public static Projection project(
      ProjectedLongTermAsset asset,
      BigDecimal openingPrincipal,
      SimulationAssumptions assumptions,
      int year,
      LocalDate asOfDate,
      BigDecimal yearFraction) {
    return project(
        asset,
        openingPrincipal,
        year,
        asOfDate,
        yearFraction,
        assumptions.fixedIncomeReturnRate(),
        assumptions.cashReturnRate());
  }

  public static Projection project(
      ProjectedLongTermAsset asset,
      BigDecimal openingPrincipal,
      SimulationScenarioSettings settings,
      int year,
      LocalDate asOfDate,
      BigDecimal yearFraction) {
    return project(
        asset,
        openingPrincipal,
        year,
        asOfDate,
        yearFraction,
        settings.fixedIncomeReturnRate(),
        settings.cashReturnRate());
  }

  private static Projection project(
      ProjectedLongTermAsset asset,
      BigDecimal openingPrincipal,
      int year,
      LocalDate asOfDate,
      BigDecimal yearFraction,
      BigDecimal fixedIncomeFallback,
      BigDecimal cashFallback) {
    if (openingPrincipal == null || openingPrincipal.signum() == 0)
      return new Projection(ZERO, ZERO, ZERO);
    if (asset.maturityDate() != null && !asset.maturityDate().isAfter(asOfDate))
      return new Projection(ZERO, ZERO, redemption(asset, openingPrincipal));

    BigDecimal netInterest =
        netInterest(
                openingPrincipal.multiply(
                    effectiveRate(asset, year, fixedIncomeFallback, cashFallback)),
                asset)
            .multiply(yearFraction);
    BigDecimal end =
        asset.interestTreatment() == InterestTreatment.CAPITALIZE
            ? openingPrincipal.add(netInterest)
            : openingPrincipal;
    BigDecimal payout = asset.interestTreatment() == InterestTreatment.PAY_OUT ? netInterest : ZERO;
    BigDecimal redemption = ZERO;
    if (asset.maturityDate() != null && asset.maturityDate().getYear() == year) {
      redemption = asset.redemptionValue() == null ? end : asset.redemptionValue();
      end = ZERO;
    }
    return new Projection(end, payout, redemption);
  }

  public static BigDecimal fullYearPayout(
      ProjectedLongTermAsset asset, SimulationAssumptions assumptions, int year) {
    if (asset.maturityDate() != null && year > asset.maturityDate().getYear()) return ZERO;
    if (asset.interestTreatment() != InterestTreatment.PAY_OUT) return ZERO;
    return netInterest(
        asset
            .currentValue()
            .multiply(
                effectiveRate(
                    asset,
                    year,
                    assumptions.fixedIncomeReturnRate(),
                    assumptions.cashReturnRate())),
        asset);
  }

  public static BigDecimal effectiveRate(
      ProjectedLongTermAsset asset, SimulationAssumptions assumptions, int year) {
    return effectiveRate(
        asset, year, assumptions.fixedIncomeReturnRate(), assumptions.cashReturnRate());
  }

  private static BigDecimal effectiveRate(
      ProjectedLongTermAsset asset,
      int year,
      BigDecimal fixedIncomeFallback,
      BigDecimal cashFallback) {
    return asset.periods().stream()
        .filter(period -> period.validFrom().getYear() <= year)
        .filter(period -> period.validTo() == null || period.validTo().getYear() >= year)
        .map(ProjectedLongTermAsset.Period::annualReturnRate)
        .findFirst()
        .orElse(
            switch (asset.bucket()) {
              case FIXED_INCOME -> fixedIncomeFallback;
              case LIQUID_CASH -> cashFallback;
              default -> ZERO;
            });
  }

  public static boolean isContractual(ProjectedLongTermAsset asset) {
    return asset.type() == LongTermAssetType.BOND
        || asset.type() == LongTermAssetType.DEPOSIT
        || (asset.bucket() == EconomicBucket.FIXED_INCOME && asset.interestTreatment() != null);
  }

  private static BigDecimal redemption(ProjectedLongTermAsset asset, BigDecimal principal) {
    return asset.redemptionValue() == null ? principal : asset.redemptionValue();
  }

  private static BigDecimal netInterest(BigDecimal gross, ProjectedLongTermAsset asset) {
    return gross.subtract(gross.multiply(Optional.ofNullable(asset.taxRate()).orElse(ZERO)));
  }

  public record Projection(
      BigDecimal endValue, BigDecimal payoutIncome, BigDecimal redemptionCash) {}
}
