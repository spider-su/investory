package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.ScenarioObservation;
import java.util.Map;

/** Public application boundary for factual scenario observations used by planning screens. */
public interface RetirementScenarioObservationApi {
  Map<String, ScenarioObservation> load(Long portfolioId, PlanningTimeline timeline);
}
