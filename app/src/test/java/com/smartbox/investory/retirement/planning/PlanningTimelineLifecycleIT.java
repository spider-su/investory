package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.retirement.api.model.CreatePlanCommand;
import com.smartbox.investory.retirement.api.model.SavePlanEventCommand;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import com.smartbox.investory.retirement.infrastructure.plan.CanonicalRetirementPlanService;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanEntity;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanRepository;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Proves that the canonical persisted HappyInvestor plan is visible to planning. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PlanningTimelineLifecycleIT extends FastDatabaseTest {
  @Autowired private CanonicalRetirementPlanService plans;
  @Autowired private RetirementPlanRepository planRows;

  @Test
  void canonicalPersistedPlanKeepsPortfolioOwnership() {
    var plan =
        plans.details(HappyInvestorTestData.PORTFOLIO_ID, HappyInvestorPlanFacts.SEED_PLAN_ID);
    assertThat(plan.name()).isEqualTo(HappyInvestorPlanFacts.NAME);
    assertThat(plan.id()).isEqualTo(HappyInvestorPlanFacts.SEED_PLAN_ID);
    assertThat(plans.listPlans(999999L)).isEmpty();
  }

  @Test
  void activePlanNamesAreCaseInsensitiveAndArchivedNamesCanBeReused() {
    var assumptions = SimulationAssumptions.defaults(40, 90, 2026);
    long original =
        plans.createPlan(
            new CreatePlanCommand(
                HappyInvestorTestData.PORTFOLIO_ID, "Freeze Name", assumptions, null));

    assertThatThrownBy(
            () ->
                plans.createPlan(
                    new CreatePlanCommand(
                        HappyInvestorTestData.PORTFOLIO_ID, "freeze name", assumptions, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plan name already exists");

    plans.deletePlan(HappyInvestorTestData.PORTFOLIO_ID, original);

    long replacement =
        plans.createPlan(
            new CreatePlanCommand(
                HappyInvestorTestData.PORTFOLIO_ID, "Freeze Name", assumptions, null));
    assertThat(replacement).isNotEqualTo(original);
  }

  @Test
  void eventMutationMovesPlanToLatestSelection() {
    var assumptions = SimulationAssumptions.defaults(40, 90, 2026);
    long edited =
        plans.createPlan(
            new CreatePlanCommand(
                HappyInvestorTestData.PORTFOLIO_ID, "Event Recency A", assumptions, null));
    long previousLatest =
        plans.createPlan(
            new CreatePlanCommand(
                HappyInvestorTestData.PORTFOLIO_ID, "Event Recency B", assumptions, null));
    RetirementPlanEntity editedRow = planRows.findById(edited).orElseThrow();
    RetirementPlanEntity latestRow = planRows.findById(previousLatest).orElseThrow();
    editedRow.setUpdatedAt(Instant.parse("2020-01-01T00:00:00Z"));
    latestRow.setUpdatedAt(Instant.parse("2021-01-01T00:00:00Z"));
    planRows.saveAndFlush(editedRow);
    planRows.saveAndFlush(latestRow);

    plans.savePlanEvent(
        new SavePlanEventCommand(
            HappyInvestorTestData.PORTFOLIO_ID,
            edited,
            null,
            2027,
            "Annual event",
            BigDecimal.ONE,
            SimulationEventType.ONE_OFF_INCOME,
            null));

    assertThat(plans.resolvePlanId(HappyInvestorTestData.PORTFOLIO_ID, null)).contains(edited);
  }
}
