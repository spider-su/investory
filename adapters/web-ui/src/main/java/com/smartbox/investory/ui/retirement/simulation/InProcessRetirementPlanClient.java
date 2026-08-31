package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementPlanClient implements RetirementPlanClient {
  private final RetirementPlanApi retirementPlanApi;

  public InProcessRetirementPlanClient(
      @Qualifier("simulationPlanService") RetirementPlanApi retirementPlanApi) {
    this.retirementPlanApi = retirementPlanApi;
  }

  public Optional<Long> resolvePlanId(Long portfolioId, Long requestedPlanId) {
    return retirementPlanApi.resolvePlanId(portfolioId, requestedPlanId);
  }

  public List<PlanSummary> listPlans(Long portfolioId) {
    return retirementPlanApi.listPlans(portfolioId);
  }

  @Override
  public PlanDetails details(Long portfolioId, Long planId) {
    return retirementPlanApi.details(portfolioId, planId);
  }

  public Long createPlan(com.smartbox.investory.retirement.api.model.CreatePlanCommand command) {
    return retirementPlanApi.createPlan(command);
  }

  public Long updatePlan(com.smartbox.investory.retirement.api.model.UpdatePlanCommand command) {
    return retirementPlanApi.updatePlan(command);
  }

  public Long savePlanEvent(
      com.smartbox.investory.retirement.api.model.SavePlanEventCommand command) {
    return retirementPlanApi.savePlanEvent(command);
  }

  public void deleteEvent(Long portfolioId, Long planId, Long eventId) {
    retirementPlanApi.deleteEvent(portfolioId, planId, eventId);
  }

  public void deletePlan(Long portfolioId, Long planId) {
    retirementPlanApi.deletePlan(portfolioId, planId);
  }

  public RevisionSummary rebaselinePlan(Long portfolioId, Long planId, PlanningBaseline baseline) {
    return retirementPlanApi.rebaselinePlan(portfolioId, planId, baseline);
  }
}
