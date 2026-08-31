package com.smartbox.investory.retirement.api.model;

/** Display values for deterministic retirement-timing analysis. */
public record RetirementAgeAnalysisMoney(
    String interpretation, Scenario base, Scenario conservative) {
  public record Scenario(
      String planned,
      String earliest,
      String headroom,
      String state,
      boolean plannedSustainable,
      boolean earliestFound) {}
}
