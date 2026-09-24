package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.rest.RetirementPlanContracts.AssumptionsDto;
import com.smartbox.investory.retirement.rest.RetirementPlanContracts.PlanCreateRequest;
import com.smartbox.investory.retirement.rest.RetirementPlanContracts.PlanUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Command Service")
class SimulationCommandServiceTest {
  private final RetirementPlanClient plans = mock(RetirementPlanClient.class);
  private final SimulationCommandService commands = new SimulationCommandService(plans);

  @Test
  @DisplayName("updates an existing plan without changing its baseline")
  void updatesExistingPlanWithoutChangingItsBaseline() {
    SimulationAssumptions assumptions = assumptions();
    PlanningBaseline baseline = mock(PlanningBaseline.class);
    var request = new PlanUpdateRequest("Plan", AssumptionsDto.from(assumptions));
    when(plans.updatePlan(1L, 7L, request)).thenReturn(7L);

    Long saved = commands.savePlan(1L, 7L, "Plan", assumptions, baseline, false);

    assertEquals(7L, saved);
    verify(plans).updatePlan(1L, 7L, request);
  }

  @Test
  @DisplayName("save as creates a new plan")
  void saveAsCreatesNewPlan() {
    SimulationAssumptions assumptions = assumptions();
    var request = new PlanCreateRequest("Copy", AssumptionsDto.from(assumptions), null);
    when(plans.createPlan(1L, request)).thenReturn(8L);

    Long saved = commands.savePlan(1L, 7L, "Copy", assumptions, null, true);

    assertEquals(8L, saved);
    verify(plans).createPlan(1L, request);
  }

  private static SimulationAssumptions assumptions() {
    return SimulationAssumptions.defaults(40, 80, 2025).toBuilder()
        .expenseProfile(ExpenseProfile.EMPTY)
        .build();
  }
}
