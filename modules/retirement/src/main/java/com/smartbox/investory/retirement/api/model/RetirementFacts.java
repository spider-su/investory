package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import java.util.Objects;

/** Immutable source facts used by retirement projection; contains no persistence objects. */
public record RetirementFacts(InvestmentProfile profile, int asOfYear) {
  public RetirementFacts {
    Objects.requireNonNull(profile, "profile");
    if (asOfYear < 1900 || asOfYear > 3000)
      throw new IllegalArgumentException("Invalid as-of year");
  }
}
