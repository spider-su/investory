package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.retirement.api.RetirementScenarioObservationApi;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.ScenarioObservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process adapter for factual scenario observations. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/retirement/scenario-observations")
public class RetirementScenarioObservationRestController {
  private final RetirementScenarioObservationApi observations;

  public RetirementScenarioObservationRestController(
      @Qualifier("retirementScenarioObservationService")
          RetirementScenarioObservationApi observations) {
    this.observations = observations;
  }

  @PostMapping
  public Map<String, ScenarioObservation> load(
      @PathVariable @Positive Long portfolioId, @Valid @RequestBody PlanningTimeline timeline) {
    return observations.load(portfolioId, timeline);
  }
}
