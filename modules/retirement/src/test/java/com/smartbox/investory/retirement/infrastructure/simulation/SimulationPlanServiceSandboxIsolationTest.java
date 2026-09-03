package com.smartbox.investory.retirement.infrastructure.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimulationPlanServiceSandboxIsolationTest {
  private final SimulationPlanRepository plans = mock(SimulationPlanRepository.class);
  private final SimulationPlanRevisionRepository revisions =
      mock(SimulationPlanRevisionRepository.class);
  private final SimulationPlanRevisionEventRepository revisionEvents =
      mock(SimulationPlanRevisionEventRepository.class);
  private final PlanningBaselineJsonCodec baselineJson = mock(PlanningBaselineJsonCodec.class);
  private SimulationPlanService service;

  @BeforeEach
  void setUp() {
    service = new SimulationPlanService(plans, revisions, revisionEvents, baselineJson);
  }

  @Test
  void sandboxResolveIsPortfolioScopedAndReturnsOnlyOneSavedSandbox() {
    when(plans.findFirstByPortfolioIdAndSandboxTrueAndArchivedFalse(1L))
        .thenReturn(Optional.of(plan(11L, 1L, true)));

    assertEquals(Optional.of(11L), service.resolve(1L));
    assertEquals(Optional.empty(), service.resolve(2L));
  }

  @Test
  void sandboxLoadCannotCrossPortfolioBoundary() {
    when(plans.findByIdAndPortfolioId(11L, 2L)).thenReturn(Optional.empty());

    assertThrows(RetirementPlanApi.PlanNotFoundException.class, () -> service.load(2L, 11L));
  }

  @Test
  void sandboxLoadRejectsNormalPlanIdentity() {
    SimulationPlanEntity normal = plan(11L, 1L, false);
    when(plans.findByIdAndPortfolioId(11L, 1L)).thenReturn(Optional.of(normal));

    assertThrows(RetirementPlanApi.PlanNotFoundException.class, () -> service.load(1L, 11L));
  }

  @Test
  void sandboxSaveCannotOverwriteAnotherPortfolioOrNormalPlan() {
    SandboxSimulationInput input = mock(SandboxSimulationInput.class);
    when(plans.findByIdAndPortfolioIdForUpdate(11L, 2L)).thenReturn(Optional.empty());
    assertThrows(RetirementPlanApi.PlanNotFoundException.class, () -> service.save(2L, 11L, input));

    when(plans.findByIdAndPortfolioIdForUpdate(11L, 1L))
        .thenReturn(Optional.of(plan(11L, 1L, false)));
    assertThrows(RetirementPlanApi.PlanNotFoundException.class, () -> service.save(1L, 11L, input));
  }

  private static SimulationPlanEntity plan(Long id, Long portfolioId, boolean sandbox) {
    SimulationPlanEntity plan = new SimulationPlanEntity();
    plan.setId(id);
    plan.setPortfolioId(portfolioId);
    plan.setSandbox(sandbox);
    plan.setArchived(false);
    plan.setName(sandbox ? "Sandbox" : "Normal");
    return plan;
  }
}
