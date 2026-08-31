package com.smartbox.investory.profile.api;

import com.smartbox.investory.profile.api.model.InvestmentProfile;

/**
 * @deprecated Use {@link ProfileSummaryReader} and {@link ProfilePlanningReader} separately.
 */
@Deprecated(forRemoval = false)
public interface ProfileReader extends ProfileSummaryReader, ProfilePlanningReader {
  InvestmentProfile loadProfile(Long portfolioId);
}
