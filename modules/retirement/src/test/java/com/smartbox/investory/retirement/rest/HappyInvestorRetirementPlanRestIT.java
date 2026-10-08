package com.smartbox.investory.retirement.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.infrastructure.plan.CanonicalRetirementPlanService;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanBaselineCodec;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanEntity;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanEventEntity;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanEventRepository;
import com.smartbox.investory.retirement.infrastructure.plan.RetirementPlanRepository;
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

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @AutoConfigurationPackage(
      basePackageClasses = {RetirementPlanEntity.class, RetirementPlanEventEntity.class})
  @EnableJpaRepositories(
      basePackageClasses = {RetirementPlanRepository.class, RetirementPlanEventRepository.class})
  @EnableTransactionManagement
  @Import({RetirementPlanRestController.class, RetirementPlanBaselineCodec.class})
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
  }
}
