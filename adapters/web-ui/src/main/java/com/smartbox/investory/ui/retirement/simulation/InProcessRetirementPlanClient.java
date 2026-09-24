package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.BaselineDto;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.EventWriteRequest;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanCreateRequest;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanDetailsDto;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanUpdateRequest;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.rest.RetirementPlanRestController;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementPlanClient implements RetirementPlanClient {
  private final RetirementPlanRestController rest;

  public InProcessRetirementPlanClient(RetirementPlanRestController rest) {
    this.rest = rest;
  }

  public Optional<Long> resolvePlanId(Long portfolioId, Long requestedPlanId) {
    return rest.resolvePlanId(portfolioId, requestedPlanId);
  }

  public List<PlanSummary> listPlans(Long portfolioId) {
    return rest.listPlans(portfolioId).stream()
        .map(dto -> new PlanSummary(dto.id(), dto.name()))
        .toList();
  }

  @Override
  public PlanDetails details(Long portfolioId, Long planId) {
    PlanDetailsDto dto = rest.details(portfolioId, planId);
    return new PlanDetails(
        dto.id(),
        dto.name(),
        dto.assumptions().toDomain(),
        dto.baseline() == null ? null : dto.baseline().toDomain());
  }

  public Long createPlan(Long portfolioId, PlanCreateRequest request) {
    return rest.createPlan(portfolioId, request).getBody().id();
  }

  public Long updatePlan(Long portfolioId, Long planId, PlanUpdateRequest request) {
    return rest.updatePlan(portfolioId, planId, request).id();
  }

  public Long savePlanEvent(
      Long portfolioId, Long planId, Long eventId, EventWriteRequest request) {
    return (eventId == null
            ? rest.createPlanEvent(portfolioId, planId, request)
            : rest.updatePlanEvent(portfolioId, planId, eventId, request))
        .id();
  }

  public void deleteEvent(Long portfolioId, Long planId, Long eventId) {
    rest.deleteEvent(portfolioId, planId, eventId);
  }

  public void deletePlan(Long portfolioId, Long planId) {
    rest.deletePlan(portfolioId, planId);
  }

  public void rebaselinePlan(Long portfolioId, Long planId, BaselineDto baseline) {
    rest.rebaselinePlan(portfolioId, planId, baseline);
  }
}
