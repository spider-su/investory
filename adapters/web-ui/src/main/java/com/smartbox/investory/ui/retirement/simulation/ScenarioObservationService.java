package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementScenarioObservationApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Adapts retirement-owned observations to the Web UI presentation model. */
@Component
public class ScenarioObservationService {
  private final RetirementScenarioObservationApi observations;

  public ScenarioObservationService(RetirementScenarioObservationApi observations) {
    this.observations = observations;
  }

  public Map<String, ScenarioObservation> load(Long portfolioId, PlanningTimeline timeline) {
    Map<String, ScenarioObservation> result = new LinkedHashMap<>();
    observations.load(portfolioId, timeline).forEach((key, value) -> result.put(key, adapt(value)));
    return result;
  }

  private static ScenarioObservation adapt(
      com.smartbox.investory.retirement.api.model.ScenarioObservation value) {
    return new ScenarioObservation(
        value.value(),
        value.label(),
        value.period(),
        ScenarioAssumptionView.Availability.valueOf(value.availability().name()));
  }
}
