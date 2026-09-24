package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.AssumptionsDto;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.BaselineDto;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.EventWriteRequest;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanCreateRequest;
import com.smartbox.investory.retirement.api.contract.RetirementPlanContracts.PlanUpdateRequest;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import com.smartbox.investory.shared.currency.CurrencyType;
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
          portfolioId,
          new PlanCreateRequest(
              name, AssumptionsDto.from(assumptions), BaselineDto.from(baseline)));
    return plans.updatePlan(
        portfolioId, planId, new PlanUpdateRequest(name, AssumptionsDto.from(assumptions)));
  }

  void saveEvent(
      Long portfolioId,
      Long planId,
      Long eventId,
      int year,
      String name,
      BigDecimal amount,
      CurrencyType displayCurrency,
      SimulationEventType type,
      String notes) {
    plans.savePlanEvent(
        portfolioId,
        planId,
        eventId,
        new EventWriteRequest(year, name, amount, displayCurrency, type, notes));
  }

  void deleteEvent(Long portfolioId, Long planId, Long eventId) {
    plans.deleteEvent(portfolioId, planId, eventId);
  }

  void deletePlan(Long portfolioId, Long planId) {
    plans.deletePlan(portfolioId, planId);
  }
}
