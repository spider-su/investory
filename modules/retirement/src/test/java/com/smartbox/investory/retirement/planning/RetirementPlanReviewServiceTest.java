package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummary;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("Retirement Plan Review Service")
class RetirementPlanReviewServiceTest {
  private final RetirementPlanApi plans = Mockito.mock(RetirementPlanApi.class);
  private final RetirementProjectionFacade projections =
      Mockito.mock(RetirementProjectionFacade.class);
  private final NotificationEventPublisher events = Mockito.mock(NotificationEventPublisher.class);
  private final ApplicationEventPublisher applicationEvents =
      Mockito.mock(ApplicationEventPublisher.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC);
  private final RetirementPlanReviewService service =
      new RetirementPlanReviewService(plans, projections, events, applicationEvents, clock);
  private final PlanningBaseline baseline = Mockito.mock(PlanningBaseline.class);
  private final com.smartbox.investory.retirement.api.model.RevisionSummary revision =
      new com.smartbox.investory.retirement.api.model.RevisionSummary(
          44L, 3, Instant.parse("2026-08-25T10:00:00Z"));

  @BeforeEach
  void setUp() {
    when(plans.rebaselinePlan(1L, 2L, baseline)).thenReturn(revision);
  }

  @DisplayName("publishes Only Sustainable To Unsustainable Reviewed Transition")
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

  @DisplayName("repeated Unsustainable Review Does Not Publish")
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
