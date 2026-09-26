package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.ScenarioObservation;
import com.smartbox.investory.retirement.rest.RetirementScenarioObservationRestController;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementScenarioObservationClient
    implements RetirementScenarioObservationClient {
  private final RetirementScenarioObservationRestController rest;

  public InProcessRetirementScenarioObservationClient(
      RetirementScenarioObservationRestController rest) {
    this.rest = rest;
  }

  @Override
  public Map<String, ScenarioObservation> load(Long portfolioId, PlanningTimeline timeline) {
    return rest.load(portfolioId, timeline);
  }
}
