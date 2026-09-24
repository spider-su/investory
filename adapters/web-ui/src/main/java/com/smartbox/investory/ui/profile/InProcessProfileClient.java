package com.smartbox.investory.ui.profile;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.web.ProfileResponse;
import com.smartbox.investory.profile.web.ProfileRestController;
import org.springframework.stereotype.Component;

@Component
public class InProcessProfileClient implements ProfileClient {
  private final ProfileRestController rest;

  public InProcessProfileClient(ProfileRestController rest) {
    this.rest = rest;
  }

  @Override
  public InvestmentProfile loadProfile(Long portfolioId) {
    ProfileResponse response = rest.profile(portfolioId);
    return new InvestmentProfile(
        response.portfolioId(),
        response.currency(),
        response.marketPortfolioValue(),
        response.longTermAssetValue(),
        response.totalNetWorth(),
        response.liquidAssets(),
        response.illiquidAssets(),
        response.allocations(),
        response.currentRentalIncome(),
        response.currentBondIncome(),
        response.longTermPlanningState(),
        response.retirementReserve(),
        response.investmentCapital(),
        response.incomeSummary(),
        response.allocationReconciliation());
  }
}
