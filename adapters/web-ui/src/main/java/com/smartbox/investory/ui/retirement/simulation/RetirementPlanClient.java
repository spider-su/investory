package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.rest.RetirementPlanContracts.BaselineDto;
import com.smartbox.investory.retirement.rest.RetirementPlanContracts.EventWriteRequest;
import com.smartbox.investory.retirement.rest.RetirementPlanContracts.PlanCreateRequest;
import com.smartbox.investory.retirement.rest.RetirementPlanContracts.PlanUpdateRequest;
import java.util.List;
import java.util.Optional;

/** UI-side client contract. Its implementation may be in-process or HTTP-backed. */
public interface RetirementPlanClient {
  Optional<Long> resolvePlanId(Long portfolioId, Long requestedPlanId);

  List<PlanSummary> listPlans(Long portfolioId);

  PlanDetails details(Long portfolioId, Long planId);

  Long createPlan(Long portfolioId, PlanCreateRequest request);

  Long updatePlan(Long portfolioId, Long planId, PlanUpdateRequest request);

  Long savePlanEvent(Long portfolioId, Long planId, Long eventId, EventWriteRequest request);

  void deleteEvent(Long portfolioId, Long planId, Long eventId);

  void deletePlan(Long portfolioId, Long planId);

  void rebaselinePlan(Long portfolioId, Long planId, BaselineDto baseline);
}
