package com.smartbox.investory.profile.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.util.CollectionUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record InvestmentProfile(
    Long portfolioId,
    CurrencyType currency,
    BigDecimal marketPortfolioValue,
    BigDecimal longTermAssetValue,
    BigDecimal totalNetWorth,
    BigDecimal liquidAssets,
    BigDecimal illiquidAssets,
    List<ProfileAllocation> allocations,
    BigDecimal currentRentalIncome,
    BigDecimal currentBondIncome,
    ProfileAssetProjection longTermPlanningState,
    BigDecimal retirementReserve,
    BigDecimal investmentCapital,
    ProfileIncomeSummary incomeSummary,
    ProfileAllocationReconciliation allocationReconciliation) {
  public InvestmentProfile {
    Objects.requireNonNull(portfolioId, "portfolioId");
    Objects.requireNonNull(currency, "currency");
    Objects.requireNonNull(marketPortfolioValue, "marketPortfolioValue");
    Objects.requireNonNull(longTermAssetValue, "longTermAssetValue");
    Objects.requireNonNull(totalNetWorth, "totalNetWorth");
    Objects.requireNonNull(liquidAssets, "liquidAssets");
    Objects.requireNonNull(illiquidAssets, "illiquidAssets");
    allocations = CollectionUtils.immutableListOrEmpty(allocations);
    currentRentalIncome = zero(currentRentalIncome);
    currentBondIncome = zero(currentBondIncome);
    Objects.requireNonNull(longTermPlanningState, "longTermPlanningState");
    Objects.requireNonNull(retirementReserve, "retirementReserve");
    Objects.requireNonNull(investmentCapital, "investmentCapital");
    Objects.requireNonNull(incomeSummary, "incomeSummary");
    Objects.requireNonNull(allocationReconciliation, "allocationReconciliation");
  }

  public BigDecimal marketPortfolioPercentage() {
    return percentageOfTotal(marketPortfolioValue);
  }

  public BigDecimal longTermAssetPercentage() {
    return percentageOfTotal(longTermAssetValue);
  }

  public BigDecimal liquidAssetsPercentage() {
    return percentageOfTotal(liquidAssets);
  }

  public BigDecimal illiquidAssetsPercentage() {
    return percentageOfTotal(illiquidAssets);
  }

  private BigDecimal percentageOfTotal(BigDecimal value) {
    return totalNetWorth == null || totalNetWorth.signum() == 0 || value == null
        ? BigDecimal.ZERO
        : value.divide(totalNetWorth, 8, RoundingMode.HALF_UP);
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
