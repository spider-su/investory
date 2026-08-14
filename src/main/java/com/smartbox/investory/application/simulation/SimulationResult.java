package com.smartbox.investory.application.simulation;

import java.math.BigDecimal;
import java.util.List;

public record SimulationResult(
    SimulationScenario scenario,
    boolean simulationFailed,
    Integer failureAge,
    BigDecimal firstFailureShortfall,
    BigDecimal totalUnfundedAmount,
    List<SimulationYear> years) {
  public SimulationResult {
    years = years == null ? List.of() : List.copyOf(years);
  }

  /** Compatibility constructor: the previous single amount was the first failure shortfall. */
  public SimulationResult(
      SimulationScenario scenario,
      boolean simulationFailed,
      Integer failureAge,
      BigDecimal firstFailureShortfall,
      List<SimulationYear> years) {
    this(
        scenario,
        simulationFailed,
        failureAge,
        firstFailureShortfall,
        firstFailureShortfall,
        years);
  }

  /**
   * @deprecated Use {@link #firstFailureShortfall()} or {@link #totalUnfundedAmount()}.
   */
  @Deprecated
  public BigDecimal unfundedAmount() {
    return firstFailureShortfall;
  }

  public SimulationYear finalYear() {
    return years.isEmpty() ? null : years.get(years.size() - 1);
  }
}
