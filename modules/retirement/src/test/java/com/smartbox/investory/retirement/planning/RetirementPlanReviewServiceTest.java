package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEntity;
import com.smartbox.investory.retirement.simulation.SimulationDecisionSummary;
import com.smartbox.investory.retirement.simulation.SimulationPlanService;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RetirementPlanReviewServiceTest {
  private final SimulationPlanService plans = Mockito.mock(SimulationPlanService.class);
  private final RetirementProjectionFacade projections =
      Mockito.mock(RetirementProjectionFacade.class);
  private final NotificationEventPublisher events = Mockito.mock(NotificationEventPublisher.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC);
  private final RetirementPlanReviewService service =
      new RetirementPlanReviewService(plans, projections, events, clock);
  private final PlanningBaseline baseline = Mockito.mock(PlanningBaseline.class);
  private final SimulationPlanRevisionEntity revision = new SimulationPlanRevisionEntity();

  @BeforeEach
  void setUp() {
    revision.setId(44L);
    revision.setRevisionNumber(3);
    when(plans.rebaseline(1L, 2L, baseline)).thenReturn(revision);
  }

  @Test
  void publishesOnlySustainableToUnsustainableReviewedTransition() {
    RetirementProjectionContext sustainable = context(summary(false));
    RetirementProjectionContext unsustainable = context(summary(true));
    when(projections.load(1L, 2L)).thenReturn(sustainable, unsustainable);

    service.rebaseline(1L, 2L, baseline);

    ArgumentCaptor<NotificationCandidate> candidate =
        ArgumentCaptor.forClass(NotificationCandidate.class);
    verify(events).publish(candidate.capture());
    assertEquals("PLAN_BECAME_UNSUSTAINABLE:2:44", candidate.getValue().fingerprint());
    assertEquals("2035", candidate.getValue().payload().get("firstFailureYear"));
  }

  @Test
  void repeatedUnsustainableReviewDoesNotPublish() {
    RetirementProjectionContext first = context(summary(true));
    RetirementProjectionContext repeated = context(summary(true));
    when(projections.load(1L, 2L)).thenReturn(first, repeated);

    service.rebaseline(1L, 2L, baseline);

    verify(events, never()).publish(any());
  }

  private static RetirementProjectionContext context(SimulationDecisionSummary summary) {
    RetirementProjectionContext context = Mockito.mock(RetirementProjectionContext.class);
    when(context.summaries()).thenReturn(Map.of(SimulationScenario.BASE, summary));
    return context;
  }

  private static SimulationDecisionSummary summary(boolean failed) {
    SimulationDecisionSummary summary = Mockito.mock(SimulationDecisionSummary.class);
    when(summary.failed()).thenReturn(failed);
    when(summary.firstFailureYear()).thenReturn(failed ? 2035 : null);
    when(summary.firstFailureAge()).thenReturn(failed ? 60 : null);
    when(summary.totalUnfundedAmount())
        .thenReturn(failed ? new BigDecimal("250000") : BigDecimal.ZERO);
    when(summary.minimumLiquidAssets()).thenReturn(failed ? BigDecimal.ZERO : BigDecimal.ONE);
    when(summary.recurringFundingGapRequired()).thenReturn(failed);
    return summary;
  }
}
