package com.smartbox.investory.application.planning;

public enum PlanningYearStatus {
  DRAFT,
  CLOSED;

  public String label() {
    return this == DRAFT ? "Draft" : "Closed";
  }
}
