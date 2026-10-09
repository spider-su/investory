package com.smartbox.investory.retirement.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.infrastructure.annualcost.RetirementAnnualCostGroupEntity;
import com.smartbox.investory.retirement.infrastructure.annualcost.RetirementAnnualCostGroupRepository;
import com.smartbox.investory.retirement.infrastructure.annualcost.RetirementAnnualCostYearEntity;
import com.smartbox.investory.retirement.infrastructure.annualcost.RetirementAnnualCostYearRepository;
import com.smartbox.investory.retirement.infrastructure.annualcost.RetirementAnnualCostsService;
import com.smartbox.investory.retirement.infrastructure.plan.CanonicalRetirementPlanService;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanBaselineCodec;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanEntity;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanEventEntity;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanEventRepository;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanRepository;
import com.smartbox.investory.retirement.infrastructure.planningyear.RetirementPlanningYearEntity;
import com.smartbox.investory.retirement.infrastructure.planningyear.RetirementPlanningYearRepository;
import com.smartbox.investory.retirement.infrastructure.planningyear.RetirementPlanningYearStateCodec;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** HTTP contracts for the canonical persisted HappyInvestor retirement plan. */
@SpringBootTest(classes = HappyInvestorRetirementPlanRestIT.TestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class HappyInvestorRetirementPlanRestIT extends FastDatabaseTest {
  private static final String BASE = "/api/v1/portfolios/2/retirement/plans";

  @Autowired private MockMvc mvc;

  @Test
  void selectionReturnsCanonicalPersistedPlanId() throws Exception {
    mvc.perform(get(BASE + "/selection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(HappyInvestorPlanFacts.SEED_PLAN_ID));
  }

  @Test
  void listReturnsCanonicalPersistedPlanSummary() throws Exception {
    mvc.perform(get(BASE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem((int) HappyInvestorPlanFacts.SEED_PLAN_ID)))
        .andExpect(jsonPath("$[*].name", hasItem(HappyInvestorPlanFacts.NAME)));
  }

  @Test
  void detailsReturnsCanonicalPersistedPlanFacts() throws Exception {
    mvc.perform(get(BASE + "/" + HappyInvestorPlanFacts.SEED_PLAN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(HappyInvestorPlanFacts.SEED_PLAN_ID))
        .andExpect(jsonPath("$.name").value(HappyInvestorPlanFacts.NAME))
        .andExpect(jsonPath("$.assumptions.currentAge").value(HappyInvestorPlanFacts.CURRENT_AGE))
        .andExpect(
            jsonPath("$.assumptions.annualLivingExpenses")
                .value(HappyInvestorPlanFacts.ANNUAL_LIVING_EXPENSES.doubleValue()))
        .andExpect(
            jsonPath("$.assumptions.annualEmploymentIncome")
                .value(HappyInvestorPlanFacts.ANNUAL_EMPLOYMENT_INCOME.doubleValue()))
        .andExpect(
            jsonPath("$.baseline.asOfYear").value(HappyInvestorPlanFacts.BASELINE_AS_OF_YEAR));
  }

  @Test
  void annualCostsReturnsCanonicalPersistedLivingAndDiscretionaryCosts() throws Exception {
    mvc.perform(
            get(BASE + "/" + HappyInvestorPlanFacts.SEED_PLAN_ID + "/annual-costs")
                .param("year", Integer.toString(HappyInvestorPlanFacts.BASELINE_AS_OF_YEAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planId").value(HappyInvestorPlanFacts.SEED_PLAN_ID))
        .andExpect(jsonPath("$.year").value(HappyInvestorPlanFacts.BASELINE_AS_OF_YEAR))
        .andExpect(jsonPath("$.planName").value(HappyInvestorPlanFacts.NAME))
        .andExpect(
            jsonPath("$.annualLivingCosts")
                .value(HappyInvestorPlanFacts.ANNUAL_LIVING_EXPENSES.doubleValue()))
        .andExpect(
            jsonPath("$.annualExtras")
                .value(HappyInvestorPlanFacts.ANNUAL_DISCRETIONARY_EXPENSES.doubleValue()));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @AutoConfigurationPackage(
      basePackageClasses = {
        RetirementPlanEntity.class,
        RetirementPlanEventEntity.class,
        RetirementAnnualCostGroupEntity.class,
        RetirementAnnualCostYearEntity.class,
        RetirementPlanningYearEntity.class
      })
  @EnableJpaRepositories(
      basePackageClasses = {
        RetirementPlanRepository.class,
        RetirementPlanEventRepository.class,
        RetirementAnnualCostGroupRepository.class,
        RetirementAnnualCostYearRepository.class,
        RetirementPlanningYearRepository.class
      })
  @EnableTransactionManagement
  @Import({
    RetirementPlanRestController.class,
    RetirementAnnualCostsRestController.class,
    RetirementPlanBaselineCodec.class
  })
  static class TestApplication {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    CanonicalRetirementPlanService canonicalRetirementPlanService(
        RetirementPlanRepository plans,
        RetirementPlanEventRepository events,
        RetirementPlanBaselineCodec baselineCodec,
        Clock clock) {
      return new CanonicalRetirementPlanService(plans, events, baselineCodec, clock);
    }

    @Bean
    RetirementPresentationApi retirementPlanningApplicationService() {
      return mock(RetirementPresentationApi.class);
    }

    @Bean
    RetirementPlanningYearStateCodec retirementPlanningYearStateCodec(
        tools.jackson.databind.ObjectMapper mapper) {
      return new RetirementPlanningYearStateCodec(mapper);
    }

    @Bean
    RetirementAnnualCostsService retirementAnnualCostsService(
        RetirementPlanRepository plans,
        RetirementAnnualCostGroupRepository groups,
        RetirementAnnualCostYearRepository costYears,
        RetirementPlanningYearRepository planningYears,
        RetirementPlanningYearStateCodec yearState,
        Clock clock) {
      return new RetirementAnnualCostsService(
          plans, groups, costYears, planningYears, yearState, clock);
    }
  }
}
