package com.smartbox.investory.ui.profile;

import com.smartbox.investory.profile.api.ProfileReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Calls the Profile public application API while UI and backend share one JVM. */
@Component
public class InProcessProfileClient implements ProfileClient {
  private final ProfileReader profileReader;

  public InProcessProfileClient(@Qualifier("profileQueryService") ProfileReader profileReader) {
    this.profileReader = profileReader;
  }

  public InvestmentProfile loadProfile(Long portfolioId) {
    return profileReader.loadProfile(portfolioId);
  }
}
