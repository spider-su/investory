package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.rest.RetirementProjectionContracts.ProjectionParameters;
import com.smartbox.investory.retirement.rest.RetirementProjectionContracts.ProjectionRequest;
import com.smartbox.investory.retirement.rest.RetirementProjectionContracts.ProjectionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/portfolios/{portfolioId}/retirement/projections")
public class RetirementProjectionRestController {
  private final RetirementProjectionApi projections;
  private final RetirementPlanApi plans;

  public RetirementProjectionRestController(
      @Qualifier("retirementProjectionService") RetirementProjectionApi projections,
      @Qualifier("canonicalRetirementPlanService") RetirementPlanApi plans) {
    this.projections = projections;
    this.plans = plans;
  }

  @PostMapping
  public ProjectionResponse project(
      @PathVariable @NotNull Long portfolioId, @Valid @RequestBody ProjectionParameters request) {
    Long effectivePlanId = plans.resolvePlanId(portfolioId, request.planId()).orElse(null);
    var projection =
        projections.load(
            portfolioId, effectivePlanId, request.defaultCurrentAge(), request.defaultEndAge());
    return ProjectionResponse.from(portfolioId, effectivePlanId, projection);
  }

  @PostMapping("/load")
  public RetirementProjection load(
      @PathVariable @NotNull Long portfolioId, @Valid @RequestBody ProjectionParameters request) {
    return projections.load(
        portfolioId, request.planId(), request.defaultCurrentAge(), request.defaultEndAge());
  }

  @PostMapping("/project")
  public RetirementProjection project(@Valid @RequestBody ProjectionRequest request) {
    return projections.project(request.profile(), request.assumptions(), request.baseline());
  }
}
