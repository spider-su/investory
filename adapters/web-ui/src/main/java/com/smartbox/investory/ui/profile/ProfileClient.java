package com.smartbox.investory.ui.profile;

import com.smartbox.investory.profile.api.model.InvestmentProfile;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface ProfileClient {
  InvestmentProfile loadProfile(Long portfolioId);
}
