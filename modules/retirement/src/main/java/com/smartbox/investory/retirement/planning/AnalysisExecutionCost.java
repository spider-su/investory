package com.smartbox.investory.retirement.planning;

/** Measurement returned by an explicit deterministic Analysis run. */
public record AnalysisExecutionCost(RetirementAnalysisResult result, long elapsedNanos, int projectedYears) {
  public AnalysisExecutionCost {
    if (result == null || elapsedNanos < 0 || projectedYears < 0)
      throw new IllegalArgumentException("Invalid Analysis execution measurement");
  }

  public double elapsedMillis() { return elapsedNanos / 1_000_000.0; }
}
