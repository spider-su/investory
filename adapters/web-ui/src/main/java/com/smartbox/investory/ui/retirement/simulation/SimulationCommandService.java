package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Coordinates UI-originated simulation commands behind one small application seam. */
@Component
final class SimulationCommandService {
  private final RetirementPlanClient plans;

  SimulationCommandService(RetirementPlanClient plans) {
    this.plans = plans;
  }

  Long savePlan(
      Long portfolioId,
      Long planId,
      String name,
      SimulationAssumptions assumptions,
      PlanningBaseline baseline,
      boolean saveAs) {
    boolean create = planId == null || saveAs;
    if (create)
      return plans.createPlan(
          new com.smartbox.investory.retirement.api.model.CreatePlanCommand(
              portfolioId, name, assumptions, baseline));
    return plans.updatePlan(
        new com.smartbox.investory.retirement.api.model.UpdatePlanCommand(
            portfolioId, planId, name, assumptions));
  }

  void saveEvent(
      Long portfolioId,
      Long planId,
      Long eventId,
      int year,
      String name,
      BigDecimal amount,
      SimulationEventType type,
      String notes) {
    plans.savePlanEvent(
        new com.smartbox.investory.retirement.api.model.SavePlanEventCommand(
            portfolioId, planId, eventId, year, name, amount, type, notes));
  }

  void deleteEvent(Long portfolioId, Long planId, Long eventId) {
    plans.deleteEvent(portfolioId, planId, eventId);
  }

  void deletePlan(Long portfolioId, Long planId) {
    plans.deletePlan(portfolioId, planId);
  }
}
