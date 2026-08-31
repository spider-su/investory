package com.smartbox.investory.retirement.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetirementPlanRestControllerTest {
  @Mock private RetirementPlanApi plans;

  @Test
  void delegatesPlanSelectionWithPortfolioResourceIdentity() {
    new RetirementPlanRestController(plans).resolvePlanId(1L, 4L);
    verify(plans).resolvePlanId(1L, 4L);
  }

  @Test
  void delegatesPlanDetailsWithPortfolioResourceIdentity() {
    var assumptions = SimulationAssumptions.defaults(40, 95, 2026);
    when(plans.details(1L, 7L))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.PlanDetails(
                7L, "Plan", assumptions, 8L, null, null));
    new RetirementPlanRestController(plans).details(1L, 7L);
    verify(plans).details(1L, 7L);
  }
}
