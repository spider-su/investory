package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Request-local cache for deterministic analysis evaluations. Never share across requests. */
final class SimulationEvaluationCache {
  private final Map<Key, SimulationEvaluation> values = new HashMap<>();

  SimulationEvaluation getOrCompute(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear,
      Supplier<SimulationEvaluation> computation) {
    return values.computeIfAbsent(
        new Key(profile, assumptions, scenario, baselineYear), ignored -> computation.get());
  }

  private record Key(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      int baselineYear) {}
}
