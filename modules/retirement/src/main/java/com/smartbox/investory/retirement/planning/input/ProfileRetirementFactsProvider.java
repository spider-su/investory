package com.smartbox.investory.retirement.planning.input;

import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.retirement.api.RetirementFactsProvider;
import com.smartbox.investory.retirement.api.model.RetirementFacts;
import com.smartbox.investory.retirement.planning.presentation.*;
import java.time.Clock;
import java.time.Year;
import org.springframework.stereotype.Service;

/** Adapts the public Profile boundary into immutable retirement source facts. */
@Service
public class ProfileRetirementFactsProvider implements RetirementFactsProvider {
  private final ProfileSnapshotReader profiles;
  private final Clock clock;

  public ProfileRetirementFactsProvider(ProfileSnapshotReader profiles, Clock clock) {
    this.profiles = profiles;
    this.clock = clock;
  }

  @Override
  public RetirementFacts load(Long portfolioId) {
    return new RetirementFacts(profiles.loadProfile(portfolioId), Year.now(clock).getValue());
  }
}
