package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.shared.util.CollectionUtils;
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
    years = CollectionUtils.immutableListOrEmpty(years);
  }

  /** Creates a result where the first failure is also the only recorded unfunded amount. */
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

  /** Returns the first failure amount used by the chart and decision views. */
  public BigDecimal unfundedAmount() {
    return firstFailureShortfall;
  }

  public SimulationYear finalYear() {
    return years.isEmpty() ? null : years.get(years.size() - 1);
  }
}
