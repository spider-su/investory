package com.smartbox.investory.profile.api.model;

import java.util.Objects;

/** Planning-only profile inputs kept separate from summary facts. */
public record ProfilePlanning(ProfileAssetProjection longTermPlanningState) {
  public ProfilePlanning {
    Objects.requireNonNull(longTermPlanningState, "longTermPlanningState");
  }
}
