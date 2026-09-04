package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEventRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanService;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Proves that plan writes create persisted immutable revisions and events. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PlanningTimelineLifecycleIT extends FastDatabaseTest {
  @Autowired private SimulationPlanService plans;
  @Autowired private SimulationPlanRevisionRepository revisions;
  @Autowired private SimulationPlanRevisionEventRepository events;

  @Test
  void publicPlanCreationPersistsRevisionAndKeepsPortfolioOwnership() {
    var assumptions =
        SimulationAssumptions.defaults(
                HappyInvestorPlanFacts.CURRENT_AGE,
                HappyInvestorPlanFacts.END_AGE,
                HappyInvestorPlanFacts.START_YEAR)
            .toBuilder()
            .retirementAge(HappyInvestorPlanFacts.RETIREMENT_AGE)
            .annualLivingExpenses(HappyInvestorPlanFacts.ANNUAL_LIVING_EXPENSES)
            .annualDiscretionaryExpenses(HappyInvestorPlanFacts.ANNUAL_DISCRETIONARY_EXPENSES)
            .annualEmploymentIncome(HappyInvestorPlanFacts.ANNUAL_EMPLOYMENT_INCOME)
            .annualPreRetirementContribution(
                HappyInvestorPlanFacts.ANNUAL_PRE_RETIREMENT_CONTRIBUTION)
            .annualPension(HappyInvestorPlanFacts.ANNUAL_PENSION)
            .pensionStartAge(HappyInvestorPlanFacts.PENSION_START_AGE)
            .inflationRate(HappyInvestorPlanFacts.INFLATION)
            .fixedIncomeReturnRate(HappyInvestorPlanFacts.FIXED_INCOME_RETURN)
            .equityReturnRate(HappyInvestorPlanFacts.EQUITY_RETURN)
            .build();
    var plan = plans.create(1L, HappyInvestorPlanFacts.NAME + " lifecycle", assumptions);
    assertThat(plan.getId()).isPositive();
    assertThat(plan.getCurrentRevisionId()).isPositive();
    assertThat(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(plan.getId()))
        .extracting("revisionNumber")
        .containsExactly(1);
    assertThat(plans.listPlans(999999L)).isEmpty();
    assertThat(events.findAllByRevisionIdOrderByYearAscIdAsc(plan.getCurrentRevisionId()))
        .isEmpty();
  }
}
