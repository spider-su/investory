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
import com.smartbox.investory.retirement.planning.application.*;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.projection.*;
import com.smartbox.investory.retirement.planning.reconciliation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.planning.timeline.*;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("Retirement Plan Review Service")
class RetirementPlanReviewServiceTest {
  private final RetirementPlanApi plans = Mockito.mock(RetirementPlanApi.class);
  private final RetirementProjectionService projections =
      Mockito.mock(RetirementProjectionService.class);
  private final NotificationEventPublisher events = Mockito.mock(NotificationEventPublisher.class);
  private final ApplicationEventPublisher applicationEvents =
      Mockito.mock(ApplicationEventPublisher.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC);
  private final RetirementPlanReviewService service =
      new RetirementPlanReviewService(plans, projections, events, applicationEvents, clock);
  private final PlanningBaseline baseline = Mockito.mock(PlanningBaseline.class);

  @DisplayName("publishes Only Sustainable To Unsustainable Reviewed Transition")
  @Test
  void publishesOnlySustainableToUnsustainableReviewedTransition() {
    RetirementProjection sustainable = context(summary(false));
    RetirementProjection unsustainable = context(summary(true));
    when(projections.load(1L, 2L)).thenReturn(sustainable, unsustainable);

    service.rebaseline(1L, 2L, baseline);

    ArgumentCaptor<NotificationCandidate> candidate =
        ArgumentCaptor.forClass(NotificationCandidate.class);
    verify(events).publish(candidate.capture());
    assertEquals("PLAN_BECAME_UNSUSTAINABLE:2", candidate.getValue().fingerprint());
    assertEquals("2035", candidate.getValue().payload().get("firstFailureYear"));
  }

  @DisplayName("repeated Unsustainable Review Does Not Publish")
  @Test
  void repeatedUnsustainableReviewDoesNotPublish() {
    RetirementProjection first = context(summary(true));
    RetirementProjection repeated = context(summary(true));
    when(projections.load(1L, 2L)).thenReturn(first, repeated);

    service.rebaseline(1L, 2L, baseline);

    verify(events, never()).publish(any());
  }

  private static RetirementProjection context(SimulationDecisionSummary summary) {
    RetirementProjection context = Mockito.mock(RetirementProjection.class);
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
