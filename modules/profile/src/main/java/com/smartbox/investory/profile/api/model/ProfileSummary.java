package com.smartbox.investory.profile.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/** Profile facts intended for summary displays and summary REST responses. */
public record ProfileSummary(
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
    BigDecimal retirementReserve,
    BigDecimal investmentCapital,
    ProfileIncomeSummary incomeSummary,
    ProfileAllocationReconciliation allocationReconciliation) {
  public static ProfileSummary from(InvestmentProfile profile) {
    return new ProfileSummary(
        profile.portfolioId(),
        profile.currency(),
        profile.marketPortfolioValue(),
        profile.longTermAssetValue(),
        profile.totalNetWorth(),
        profile.liquidAssets(),
        profile.illiquidAssets(),
        profile.allocations(),
        profile.currentRentalIncome(),
        profile.currentBondIncome(),
        profile.retirementReserve(),
        profile.investmentCapital(),
        profile.incomeSummary(),
        profile.allocationReconciliation());
  }
}
