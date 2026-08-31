package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Creates the canonical current-to-forward simulation boundary without mutating saved plans. */
@Service
public class ForwardSimulationContextFactory {
  private final Clock clock;

  public ForwardSimulationContextFactory(Clock clock) {
    this.clock = clock;
  }

  public ForwardSimulationContext create(
      InvestmentProfile currentProfile, SimulationAssumptions originalAssumptions) {
    int asOfYear = Year.now(clock).getValue();
    if (asOfYear < originalAssumptions.startYear()) {
      throw new IllegalArgumentException("Forward context cannot precede the plan start year");
    }

    int asOfAge = currentPlanningAge(originalAssumptions, asOfYear);
    if (asOfAge > originalAssumptions.endAge()) {
      throw new IllegalArgumentException("Forward context is beyond the plan horizon");
    }

    int firstProjectedYear = asOfYear + 1;
    int firstProjectedAge = asOfAge + 1;
    List<SimulationEvent> currentYearEvents =
        originalAssumptions.futureEvents().stream()
            .filter(event -> event.year() == asOfYear)
            .toList();
    List<SimulationEvent> remainingEvents =
        originalAssumptions.futureEvents().stream()
            .filter(event -> event.year() > asOfYear)
            .toList();
    int excludedEvents =
        (int)
            originalAssumptions.futureEvents().stream()
                .filter(event -> event.year() < asOfYear)
                .count();
    Optional<SimulationAssumptions> forwardAssumptions =
        firstProjectedAge <= originalAssumptions.endAge()
            ? Optional.of(
                originalAssumptions.rebasedTo(
                    firstProjectedAge, firstProjectedYear, remainingEvents))
            : Optional.empty();

    return new ForwardSimulationContext(
        currentProfile,
        originalAssumptions,
        originalAssumptions.startYear(),
        originalAssumptions.currentAge(),
        asOfYear,
        asOfAge,
        firstProjectedYear,
        firstProjectedAge,
        forwardAssumptions,
        currentYearEvents,
        remainingEvents,
        excludedEvents,
        forwardAssumptions.isPresent());
  }

  /** Returns the age represented by a saved plan at the supplied calendar year. */
  public static int currentPlanningAge(SimulationAssumptions assumptions, int calendarYear) {
    return assumptions.ageAtPlanStart() + calendarYear - assumptions.planStartYear();
  }

  public static int retirementYear(SimulationAssumptions assumptions) {
    return assumptions.planStartYear() + assumptions.retirementAge() - assumptions.ageAtPlanStart();
  }
}
