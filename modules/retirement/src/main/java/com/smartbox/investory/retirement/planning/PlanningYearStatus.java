package com.smartbox.investory.retirement.planning;

public enum PlanningYearStatus {
  DRAFT,
  CLOSED;

  public String label() {
    return this == DRAFT ? "Draft" : "Closed";
  }
}
