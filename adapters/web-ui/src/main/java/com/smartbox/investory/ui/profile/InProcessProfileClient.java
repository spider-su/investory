package com.smartbox.investory.ui.profile;

import com.smartbox.investory.profile.api.ProfileComposition;
import com.smartbox.investory.profile.api.ProfilePlanningReader;
import com.smartbox.investory.profile.api.ProfileSummaryReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Calls the Profile public application API while UI and backend share one JVM. */
@Component
public class InProcessProfileClient implements ProfileClient {
  private final ProfileSummaryReader summaries;
  private final ProfilePlanningReader planning;

  public InProcessProfileClient(
      @Qualifier("profileQueryService") ProfileSummaryReader summaries,
      @Qualifier("profileQueryService") ProfilePlanningReader planning) {
    this.summaries = summaries;
    this.planning = planning;
  }

  public InvestmentProfile loadProfile(Long portfolioId) {
    return ProfileComposition.load(summaries, planning, portfolioId);
  }
}
