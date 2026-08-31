package com.smartbox.investory.profile.api;

import com.smartbox.investory.profile.api.model.ProfilePlanning;

/** Public read boundary for planning inputs derived from the current profile. */
public interface ProfilePlanningReader {
  ProfilePlanning loadPlanning(Long portfolioId);
}
