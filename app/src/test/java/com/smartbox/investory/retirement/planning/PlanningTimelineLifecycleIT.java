package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEventRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanService;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Proves that the canonical persisted HappyInvestor plan is visible to planning. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PlanningTimelineLifecycleIT extends FastDatabaseTest {
  @Autowired private SimulationPlanService plans;
  @Autowired private SimulationPlanRevisionRepository revisions;
  @Autowired private SimulationPlanRevisionEventRepository events;

  @Test
  void canonicalPersistedPlanKeepsPortfolioOwnership() {
    var plan =
        plans.details(HappyInvestorTestData.PORTFOLIO_ID, HappyInvestorPlanFacts.SEED_PLAN_ID);
    assertThat(plan.name()).isEqualTo(HappyInvestorPlanFacts.NAME);
    assertThat(plan.currentRevisionId()).isEqualTo(HappyInvestorPlanFacts.SEED_REVISION_ID);
    assertThat(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(plan.id()))
        .extracting("revisionNumber")
        .containsExactly(1);
    assertThat(plans.listPlans(999999L)).isEmpty();
    assertThat(events.findAllByRevisionIdOrderByYearAscIdAsc(plan.currentRevisionId())).isEmpty();
  }
}
