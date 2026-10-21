package com.smartbox.investory.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Full application HTTP coverage for the persisted HappyInvestor retirement profile. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test-fast")
@TestPropertySource(properties = "investory.time.fixed-instant=2026-01-01T00:00:00Z")
class HappyInvestorRetirementProfileRestIT {
  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("happyinvestor_retirement_profile");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Autowired private MockMvc mvc;

  @Test
  void annualCostReturnsSelectedPersistedPlanThroughApplicationSecurityAndRestWiring()
      throws Exception {
    var response =
        mvc.perform(
                get("/api/v1/portfolios/2/retirement/profile/annual-cost")
                    .param("reportingCurrency", "PLN")
                    .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.amount").isNumber())
            .andExpect(jsonPath("$.currency").value("PLN"))
            .andExpect(jsonPath("$.year").value(2026))
            .andExpect(jsonPath("$.planId").value(HappyInvestorPlanFacts.SEED_PLAN_ID))
            .andReturn()
            .getResponse()
            .getContentAsString();
    BigDecimal amount =
        com.jayway.jsonpath.JsonPath.parse(response).read("$.amount", BigDecimal.class);
    BigDecimal baselineCosts =
        HappyInvestorPlanFacts.ANNUAL_LIVING_EXPENSES.add(
            HappyInvestorPlanFacts.ANNUAL_DISCRETIONARY_EXPENSES);
    // The saved recurring budget is the Live-year outlook. Growth begins next year.
    assertThat(amount).isEqualByComparingTo(baselineCosts);
  }

  @Test
  void pastYearReturnsCanonicalPersistedTimelineFactsThroughApplicationWiring() throws Exception {
    mvc.perform(
            get("/api/v1/portfolios/2/retirement/timeline/years/2025")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.year").value(2025))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.values.NET_WORTH.derivedValue").value(1179307.015664))
        .andExpect(jsonPath("$.values.CORE_SPENDING.approvedValue").value(36000))
        .andExpect(jsonPath("$.values.DISCRETIONARY_SPENDING.approvedValue").value(6000));
  }

  @Test
  void reviewModeRecognizesThePersistedHistoricalPlanningYear() throws Exception {
    mvc.perform(
            get("/api/v1/portfolios/2/retirement/timeline/years/2025/mode")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value("CLOSED"));
  }

  @Test
  void manualSpendingMetricIsEditableForThePersistedDraftYear() throws Exception {
    mvc.perform(
            get("/api/v1/portfolios/2/retirement/timeline/years/2025/metrics/CORE_SPENDING/editable")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(true));
  }

  @Test
  void closeStatusUsesThePersistedHappyInvestorYearFacts() throws Exception {
    mvc.perform(
            get("/api/v1/portfolios/2/retirement/timeline/years/2025/close-status")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canClose").value(true))
        .andExpect(jsonPath("$.missingMetrics").isEmpty());
  }
}
