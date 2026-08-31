package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEntity;
import com.smartbox.investory.retirement.simulation.SimulationDecisionSummary;
import com.smartbox.investory.retirement.simulation.SimulationPlanService;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the explicit reviewed-revision boundary and its sustainability transition event. */
@Service
@RequiredArgsConstructor
public class RetirementPlanReviewService {
  private final SimulationPlanService plans;
  private final RetirementProjectionFacade projections;
  private final NotificationEventPublisher events;
  private final Clock clock;

  @Transactional
  public SimulationPlanRevisionEntity rebaseline(
      Long portfolioId, Long planId, PlanningBaseline baseline) {
    SimulationDecisionSummary previous = baseSummary(projections.load(portfolioId, planId));
    SimulationPlanRevisionEntity revision = plans.rebaseline(portfolioId, planId, baseline);
    SimulationDecisionSummary reviewed = baseSummary(projections.load(portfolioId, planId));
    if (previous != null && !previous.failed() && reviewed != null && reviewed.failed()) {
      publishTransition(portfolioId, planId, revision, reviewed);
    }
    return revision;
  }

  private void publishTransition(
      Long portfolioId,
      Long planId,
      SimulationPlanRevisionEntity revision,
      SimulationDecisionSummary summary) {
    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("portfolioId", portfolioId.toString());
    payload.put("planId", planId.toString());
    payload.put("revisionId", revision.getId().toString());
    payload.put("revisionNumber", Integer.toString(revision.getRevisionNumber()));
    payload.put("firstFailureYear", value(summary.firstFailureYear()));
    payload.put("firstFailureAge", value(summary.firstFailureAge()));
    payload.put("totalUnfundedAmount", money(summary.totalUnfundedAmount()));
    payload.put("minimumLiquidAssets", money(summary.minimumLiquidAssets()));
    payload.put("limitingCondition", limitingCondition(summary));
    events.publish(
        new NotificationCandidate(
            NotificationEventType.PLAN_BECAME_UNSUSTAINABLE,
            NotificationSeverity.CRITICAL,
            portfolioId,
            "SIMULATION_PLAN_REVISION",
            revision.getId().toString(),
            "PLAN_BECAME_UNSUSTAINABLE:" + planId + ":" + revision.getId(),
            "Retirement plan became unsustainable",
            payload,
            clock.instant()));
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
