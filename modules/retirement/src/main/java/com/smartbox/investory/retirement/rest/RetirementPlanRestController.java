package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.BaselineDto;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.EventWriteRequest;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanCreateRequest;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanDetailsDto;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanMutationResponse;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanSummaryDto;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanUpdateRequest;
import com.smartbox.investory.retirement.api.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stable HTTP adapter for retirement plan resources. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/retirement/plans")
public class RetirementPlanRestController {
  private final RetirementPlanApi plans;
  private final RetirementPresentationApi presentation;

  @Autowired
  public RetirementPlanRestController(
      @Qualifier("canonicalRetirementPlanService") RetirementPlanApi plans,
      @Qualifier("retirementPlanningApplicationService") RetirementPresentationApi presentation) {
    this.plans = plans;
    this.presentation = presentation;
  }

  public RetirementPlanRestController(RetirementPlanApi plans) {
    this.plans = plans;
    this.presentation = null;
  }

  @GetMapping("/selection")
  public Optional<Long> resolvePlanId(
      @PathVariable @NotNull Long portfolioId,
      @RequestParam(required = false) Long requestedPlanId) {
    return plans.resolvePlanId(portfolioId, requestedPlanId);
  }

  @GetMapping
  public List<PlanSummaryDto> listPlans(@PathVariable @NotNull Long portfolioId) {
    return plans.listPlans(portfolioId).stream().map(PlanSummaryDto::from).toList();
  }

  @GetMapping("/{planId}")
  public PlanDetailsDto details(
      @PathVariable @NotNull Long portfolioId, @PathVariable @NotNull Long planId) {
    return PlanDetailsDto.from(plans.details(portfolioId, planId));
  }

  @PostMapping
  public ResponseEntity<PlanMutationResponse> createPlan(
      @PathVariable @NotNull Long portfolioId, @Valid @RequestBody PlanCreateRequest request) {
    Long id =
        plans.createPlan(
            new com.smartbox.investory.retirement.api.model.CreatePlanCommand(
                portfolioId,
                request.name(),
                request.assumptions().toDomain(),
                request.baseline() == null ? null : request.baseline().toDomain()));
    return ResponseEntity.created(
            URI.create("/api/v1/portfolios/" + portfolioId + "/retirement/plans/" + id))
        .body(new PlanMutationResponse(id));
  }

  @PutMapping("/{planId}")
  public PlanMutationResponse updatePlan(
      @PathVariable @NotNull Long portfolioId,
      @PathVariable @NotNull Long planId,
      @Valid @RequestBody PlanUpdateRequest request) {
    Long id =
        plans.updatePlan(
            new com.smartbox.investory.retirement.api.model.UpdatePlanCommand(
                portfolioId, planId, request.name(), request.assumptions().toDomain()));
    return new PlanMutationResponse(id);
  }

  @PostMapping("/{planId}/events")
  public PlanMutationResponse createPlanEvent(
      @PathVariable @NotNull Long portfolioId,
      @PathVariable @NotNull Long planId,
      @Valid @RequestBody EventWriteRequest request) {
    return savePlanEvent(portfolioId, planId, null, request);
  }

  @PutMapping("/{planId}/events/{eventId}")
  public PlanMutationResponse updatePlanEvent(
      @PathVariable @NotNull Long portfolioId,
      @PathVariable @NotNull Long planId,
      @PathVariable @NotNull Long eventId,
      @Valid @RequestBody EventWriteRequest request) {
    return savePlanEvent(portfolioId, planId, eventId, request);
  }

  private PlanMutationResponse savePlanEvent(
      Long portfolioId, Long planId, Long eventId, EventWriteRequest request) {
    Long savedId =
        plans.savePlanEvent(
            new com.smartbox.investory.retirement.api.model.SavePlanEventCommand(
                portfolioId,
                planId,
                eventId,
                request.year(),
                request.name(),
                presentation.fromDisplay(
                    request.amount(), request.currency(), java.math.BigDecimal.ZERO),
                request.type(),
                request.notes()));
    return new PlanMutationResponse(savedId);
  }

  @DeleteMapping("/{planId}/events/{eventId}")
  public ResponseEntity<Void> deleteEvent(
      @PathVariable @NotNull Long portfolioId,
      @PathVariable @NotNull Long planId,
      @PathVariable @NotNull Long eventId) {
    plans.deleteEvent(portfolioId, planId, eventId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{planId}")
  public ResponseEntity<Void> deletePlan(
      @PathVariable @NotNull Long portfolioId, @PathVariable @NotNull Long planId) {
    plans.deletePlan(portfolioId, planId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{planId}/rebaseline")
  public PlanMutationResponse rebaselinePlan(
      @PathVariable @NotNull Long portfolioId,
      @PathVariable @NotNull Long planId,
      @Valid @RequestBody BaselineDto baseline) {
    plans.rebaselinePlan(portfolioId, planId, baseline.toDomain());
    return new PlanMutationResponse(planId);
  }
}
