package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummary;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import com.smartbox.investory.shared.notifications.PlanRevisionReviewedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the explicit reviewed-revision boundary and its sustainability transition event. */
@Service
@RequiredArgsConstructor
public class RetirementPlanReviewService {
  private final RetirementPlanApi plans;
  private final RetirementProjectionFacade projections;
  private final NotificationEventPublisher events;
  private final ApplicationEventPublisher applicationEvents;
  private final Clock clock;

  @Transactional
  public com.smartbox.investory.retirement.api.model.RevisionSummary rebaseline(
      Long portfolioId, Long planId, PlanningBaseline baseline) {
    SimulationDecisionSummary previous = baseSummary(projections.load(portfolioId, planId));
    com.smartbox.investory.retirement.api.model.RevisionSummary revision =
        plans.rebaselinePlan(portfolioId, planId, baseline);
    SimulationDecisionSummary reviewed = baseSummary(projections.load(portfolioId, planId));
    if (previous != null && !previous.failed() && reviewed != null && reviewed.failed()) {
      publishTransition(portfolioId, planId, revision, reviewed);
    }
    return revision;
  }

  private void publishTransition(
      Long portfolioId,
      Long planId,
      com.smartbox.investory.retirement.api.model.RevisionSummary revision,
      SimulationDecisionSummary summary) {
    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("portfolioId", portfolioId.toString());
    payload.put("planId", planId.toString());
    payload.put("revisionId", revision.id().toString());
    payload.put("revisionNumber", Integer.toString(revision.revisionNumber()));
    payload.put("firstFailureYear", value(summary.firstFailureYear()));
    payload.put("firstFailureAge", value(summary.firstFailureAge()));
    payload.put("totalUnfundedAmount", money(summary.totalUnfundedAmount()));
    payload.put("minimumLiquidAssets", money(summary.minimumLiquidAssets()));
    payload.put("limitingCondition", limitingCondition(summary));
    NotificationCandidate candidate =
        new NotificationCandidate(
            NotificationEventType.PLAN_BECAME_UNSUSTAINABLE,
            NotificationSeverity.CRITICAL,
            portfolioId,
            "SIMULATION_PLAN_REVISION",
            revision.id().toString(),
            "PLAN_BECAME_UNSUSTAINABLE:" + planId + ":" + revision.id(),
            "Retirement plan became unsustainable",
            payload,
            clock.instant());
    if (events.publish(candidate)) {
      applicationEvents.publishEvent(new PlanRevisionReviewedEvent(candidate));
    }
  }

  private static SimulationDecisionSummary baseSummary(RetirementProjectionContext projection) {
    return projection.summaries().get(SimulationScenario.BASE);
  }

  private static String limitingCondition(SimulationDecisionSummary summary) {
    if (summary.recurringFundingGapRequired()) return "Recurring funding gap";
    if (summary.minimumLiquidAssets() != null
        && summary.minimumLiquidAssets().compareTo(BigDecimal.ZERO) <= 0)
      return "Liquid assets depleted";
    return "Funding needs exceed available assets";
  }

  private static String value(Object value) {
    return value == null ? "Unavailable" : value.toString();
  }

  private static String money(BigDecimal value) {
    return value == null ? "Unavailable" : value.stripTrailingZeros().toPlainString();
  }
}
