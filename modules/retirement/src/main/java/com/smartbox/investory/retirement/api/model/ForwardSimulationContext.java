package com.smartbox.investory.retirement.api.model;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable boundary between saved-plan history and the next forward projection.
 *
 * <p>The saved assumptions remain available as the original plan reference. The optional rebased
 * assumptions describe the first complete projected year; current-year bridging is intentionally
 * represented but not performed by this context.
 */
public record ForwardSimulationContext(
    InvestmentProfile currentProfile,
    SimulationAssumptions originalAssumptions,
    int originalStartYear,
    int originalCurrentAge,
    int asOfYear,
    int asOfAge,
    int firstProjectedYear,
    int firstProjectedAge,
    Optional<SimulationAssumptions> forwardAssumptions,
    List<SimulationEvent> currentYearEvents,
    List<SimulationEvent> remainingFutureEvents,
    int historicalEventCountExcluded,
    boolean requiresCurrentYearBridge) {
  public ForwardSimulationContext {
    Objects.requireNonNull(currentProfile, "currentProfile");
    Objects.requireNonNull(originalAssumptions, "originalAssumptions");
    Objects.requireNonNull(forwardAssumptions, "forwardAssumptions");
    remainingFutureEvents = List.copyOf(remainingFutureEvents);
    currentYearEvents = List.copyOf(currentYearEvents);
  }

  public boolean futureProjectionAvailable() {
    return forwardAssumptions.isPresent();
  }
}
