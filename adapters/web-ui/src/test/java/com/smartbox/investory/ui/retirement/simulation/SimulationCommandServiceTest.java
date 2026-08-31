package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Command Service")
class SimulationCommandServiceTest {
  private final RetirementPlanClient plans = mock(RetirementPlanClient.class);
  private final SimulationCommandService commands = new SimulationCommandService(plans);

  @Test
  @DisplayName("updates an existing plan without changing its baseline")
  void updatesExistingPlanWithoutChangingItsBaseline() {
    SimulationAssumptions assumptions = mock(SimulationAssumptions.class);
    PlanningBaseline baseline = mock(PlanningBaseline.class);
    var command =
        new com.smartbox.investory.retirement.api.model.UpdatePlanCommand(
            1L, 7L, "Plan", assumptions);
    when(plans.updatePlan(command)).thenReturn(7L);

    Long saved = commands.savePlan(1L, 7L, "Plan", assumptions, baseline, false);

    assertEquals(7L, saved);
    verify(plans).updatePlan(command);
  }

  @Test
  @DisplayName("save as creates a new plan")
  void saveAsCreatesNewPlan() {
    SimulationAssumptions assumptions = mock(SimulationAssumptions.class);
    var command =
        new com.smartbox.investory.retirement.api.model.CreatePlanCommand(
            1L, "Copy", assumptions, null);
    when(plans.createPlan(command)).thenReturn(8L);

    Long saved = commands.savePlan(1L, 7L, "Copy", assumptions, null, true);

    assertEquals(8L, saved);
    verify(plans).createPlan(command);
  }
}
