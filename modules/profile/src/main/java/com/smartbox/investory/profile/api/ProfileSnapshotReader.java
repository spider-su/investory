package com.smartbox.investory.profile.api;

import com.smartbox.investory.profile.api.model.InvestmentProfile;

/** Reads the complete profile inside one transaction snapshot for reproducible consumers. */
public interface ProfileSnapshotReader {
  InvestmentProfile loadProfile(Long portfolioId);
}
