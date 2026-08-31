package com.smartbox.investory.retirement.api.model;

public enum PlanningYearStatus {
  DRAFT,
  CLOSED;

  public String label() {
    return this == DRAFT ? "Draft" : "Closed";
  }
}
