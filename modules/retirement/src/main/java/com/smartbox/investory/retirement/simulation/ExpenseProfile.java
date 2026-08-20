package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.util.List;

/** Immutable schedule for structural, non-inflationary changes in recurring spending. */
public record ExpenseProfile(List<ExpenseProfileStep> steps) {
  public static final BigDecimal DEFAULT_FACTOR = new BigDecimal("1.00");
  public static final ExpenseProfile EMPTY = new ExpenseProfile(List.of());

  public ExpenseProfile {
    if (steps == null) throw new IllegalArgumentException("Expense profile steps are required");
    int previousYear = -1;
    for (ExpenseProfileStep step : steps) {
      if (step == null) throw new IllegalArgumentException("Expense profile step is required");
      if (step.fromYear() <= previousYear)
        throw new IllegalArgumentException("Expense profile years must be strictly increasing");
      previousYear = step.fromYear();
    }
    steps = List.copyOf(steps);
  }

  public BigDecimal factorForYear(int relativeYear) {
    BigDecimal result = DEFAULT_FACTOR;
    for (ExpenseProfileStep step : steps) {
      if (step.fromYear() > relativeYear) break;
      result = step.factor();
    }
    return result;
  }

  /** Re-expresses this schedule when a simulation is rebased to a later relative year. */
  public ExpenseProfile rebasedAt(int relativeYear) {
    if (relativeYear <= 0 || steps.isEmpty()) return this;
    List<ExpenseProfileStep> rebased =
        new java.util.ArrayList<>(List.of(new ExpenseProfileStep(0, factorForYear(relativeYear))));
    steps.stream()
        .filter(step -> step.fromYear() > relativeYear)
        .map(step -> new ExpenseProfileStep(step.fromYear() - relativeYear, step.factor()))
        .forEach(rebased::add);
    return new ExpenseProfile(rebased);
  }
}
