package com.smartbox.investory.profile.web;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import org.springframework.stereotype.Component;

/** Explicit mapper from the Profile application read model to its REST contract. */
@Component
public final class ProfileResponseMapper {
  public ProfileResponse map(InvestmentProfile profile) {
    return new ProfileResponse(
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
        profile.longTermPlanningState(),
        profile.retirementReserve(),
        profile.investmentCapital(),
        profile.incomeSummary(),
        profile.allocationReconciliation());
  }
}
