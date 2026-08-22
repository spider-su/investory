package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

/** A real-spending adjustment that starts at a simulation-relative year. */
public record ExpenseProfileStep(int fromYear, BigDecimal factor) {
  public ExpenseProfileStep {
    if (fromYear < 0) throw new IllegalArgumentException("Expense profile year cannot be negative");
    if (factor == null || factor.signum() < 0)
      throw new IllegalArgumentException("Expense profile factor cannot be negative");
  }
}
