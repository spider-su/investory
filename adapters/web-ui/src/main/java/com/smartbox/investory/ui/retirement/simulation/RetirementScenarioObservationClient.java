package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import java.util.Map;

/** UI boundary for factual retirement scenario observations. */
public interface RetirementScenarioObservationClient {
  Map<String, com.smartbox.investory.retirement.api.model.ScenarioObservation> load(
      Long portfolioId, PlanningTimeline timeline);
}
