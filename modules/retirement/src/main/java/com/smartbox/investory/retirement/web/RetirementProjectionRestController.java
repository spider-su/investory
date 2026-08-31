package com.smartbox.investory.retirement.web;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.web.RetirementProjectionContracts.ProjectionParameters;
import com.smartbox.investory.retirement.web.RetirementProjectionContracts.ProjectionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stable HTTP adapter for prepared retirement projections. */
@RestController
@Validated
@RequestMapping("/api/v1/retirement/portfolios/{portfolioId}/projections")
public class RetirementProjectionRestController {
  private final RetirementProjectionApi projections;
  private final RetirementPlanApi plans;

  @Autowired
  public RetirementProjectionRestController(
      @Qualifier("retirementProjectionFacade") RetirementProjectionApi projections,
      @Qualifier("simulationPlanService") RetirementPlanApi plans) {
    this.projections = projections;
    this.plans = plans;
  }

  /** Compatibility constructor for narrow standalone controller tests. */
  public RetirementProjectionRestController(RetirementProjectionApi projections) {
    this.projections = projections;
    this.plans = null;
  }

  @PostMapping
  public ProjectionResponse project(
      @PathVariable @NotNull Long portfolioId, @Valid @RequestBody ProjectionParameters request) {
    Long effectivePlanId =
        plans == null
            ? request.planId()
            : plans.resolvePlanId(portfolioId, request.planId()).orElse(null);
    var projection =
        projections.load(
            portfolioId,
            effectivePlanId,
            request.defaultCurrentAge(),
            request.defaultEndAge(),
            request.customDeltas());
    return ProjectionResponse.from(portfolioId, effectivePlanId, projection);
  }
}
