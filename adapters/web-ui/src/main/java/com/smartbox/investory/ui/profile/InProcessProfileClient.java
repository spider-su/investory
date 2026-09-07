package com.smartbox.investory.ui.profile;

import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Calls the Profile public application API while UI and backend share one JVM. */
@Component
public class InProcessProfileClient implements ProfileClient {
  private final ProfileSnapshotReader profiles;

  public InProcessProfileClient(@Qualifier("profileQueryService") ProfileSnapshotReader profiles) {
    this.profiles = profiles;
  }

  public InvestmentProfile loadProfile(Long portfolioId) {
    return profiles.loadProfile(portfolioId);
  }
}
