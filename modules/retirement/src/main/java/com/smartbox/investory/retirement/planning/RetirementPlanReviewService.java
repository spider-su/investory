package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummary;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import com.smartbox.investory.shared.notifications.RetirementPlanReviewedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns explicit plan rebaselining and its sustainability transition event. */
@Service
@RequiredArgsConstructor
public class RetirementPlanReviewService {
  private final RetirementPlanApi plans;
  private final RetirementProjectionService projections;
  private final NotificationEventPublisher events;
  private final ApplicationEventPublisher applicationEvents;
  private final Clock clock;

  @Transactional
  public void rebaseline(Long portfolioId, Long planId, PlanningBaseline baseline) {
    SimulationDecisionSummary previous = baseSummary(projections.load(portfolioId, planId));
    plans.rebaselinePlan(portfolioId, planId, baseline);
    SimulationDecisionSummary reviewed = baseSummary(projections.load(portfolioId, planId));
    if (previous != null && !previous.failed() && reviewed != null && reviewed.failed()) {
      publishTransition(portfolioId, planId, reviewed);
    }
  }

  private void publishTransition(Long portfolioId, Long planId, SimulationDecisionSummary summary) {
    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("portfolioId", portfolioId.toString());
    payload.put("planId", planId.toString());
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
            "RETIREMENT_PLAN",
            planId.toString(),
            "PLAN_BECAME_UNSUSTAINABLE:" + planId,
            "Retirement plan became unsustainable",
            payload,
            clock.instant());
    if (events.publish(candidate)) {
      applicationEvents.publishEvent(new RetirementPlanReviewedEvent(candidate));
    }
  }

  private static SimulationDecisionSummary baseSummary(RetirementProjection projection) {
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
