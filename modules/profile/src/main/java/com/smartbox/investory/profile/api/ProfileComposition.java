package com.smartbox.investory.profile.api;

import com.smartbox.investory.profile.api.model.InvestmentProfile;

/**
 * Composes two independent profile reads; callers needing one snapshot use ProfileSnapshotReader.
 */
public final class ProfileComposition {
  private ProfileComposition() {}

  public static InvestmentProfile load(
      ProfileSummaryReader summaries, ProfilePlanningReader planning, Long portfolioId) {
    return InvestmentProfile.from(
        summaries.loadSummary(portfolioId), planning.loadPlanning(portfolioId));
  }
}
