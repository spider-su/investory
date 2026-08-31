package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.CreatePlanCommand;
import com.smartbox.investory.retirement.api.model.PlanDetails;
import com.smartbox.investory.retirement.api.model.PlanSummary;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.RevisionSummary;
import com.smartbox.investory.retirement.api.model.SavePlanEventCommand;
import com.smartbox.investory.retirement.api.model.UpdatePlanCommand;
import java.util.List;
import java.util.Optional;

/** Public plan-management boundary. Persistence entities never cross this contract. */
public interface RetirementPlanApi {
  Optional<Long> resolvePlanId(Long portfolioId, Long requestedPlanId);

  List<PlanSummary> listPlans(Long portfolioId);

  PlanDetails details(Long portfolioId, Long planId);

  Long createPlan(CreatePlanCommand command);

  Long updatePlan(UpdatePlanCommand command);

  Long savePlanEvent(SavePlanEventCommand command);

  void deleteEvent(Long portfolioId, Long planId, Long eventId);

  void deletePlan(Long portfolioId, Long planId);

  RevisionSummary rebaselinePlan(Long portfolioId, Long planId, PlanningBaseline baseline);

  final class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException() {
      super("Simulation plan not found");
    }
  }

  final class RevisionNotFoundException extends RuntimeException {
    public RevisionNotFoundException() {
      super("Simulation plan revision not found");
    }
  }

  final class EventNotFoundException extends RuntimeException {
    public EventNotFoundException() {
      super("Simulation event not found");
    }
  }
}
