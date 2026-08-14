package com.smartbox.investory.application.simulation;

import com.smartbox.investory.application.profile.InvestmentProfile;
import java.util.List;
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
    if (currentProfile == null || originalAssumptions == null || forwardAssumptions == null) {
      throw new IllegalArgumentException("Forward simulation context requires source values");
    }
    remainingFutureEvents = List.copyOf(remainingFutureEvents);
    currentYearEvents = List.copyOf(currentYearEvents);
  }

  public boolean futureProjectionAvailable() {
    return forwardAssumptions.isPresent();
  }
}
