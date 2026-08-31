package com.smartbox.investory.retirement.api.model;

/** Outcome of the deterministic recurring-spending boundary search. */
public enum SustainableSpendingResultState {
  BOUNDARY_FOUND,
  NO_SUSTAINABLE_SPENDING,
  UPPER_BOUND_NOT_FOUND,
  NON_MONOTONIC_RESULT
}
