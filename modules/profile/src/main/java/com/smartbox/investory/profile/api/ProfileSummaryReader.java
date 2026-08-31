package com.smartbox.investory.profile.api;

import com.smartbox.investory.profile.api.model.ProfileSummary;

/** Public read boundary for whole-wealth summary facts. */
public interface ProfileSummaryReader {
  ProfileSummary loadSummary(Long portfolioId);
}
