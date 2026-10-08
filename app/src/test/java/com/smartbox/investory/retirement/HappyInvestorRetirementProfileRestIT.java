package com.smartbox.investory.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorPlanFacts;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

/** Full application HTTP coverage for the persisted HappyInvestor retirement profile. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class HappyInvestorRetirementProfileRestIT extends FastDatabaseTest {
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
            .andExpect(jsonPath("$.year").value(LocalDate.now().getYear()))
            .andExpect(jsonPath("$.planId").value(HappyInvestorPlanFacts.SEED_PLAN_ID))
            .andReturn()
            .getResponse()
            .getContentAsString();
    BigDecimal amount = com.jayway.jsonpath.JsonPath.read(response, "$.amount");
    assertThat(amount).isPositive();
  }
}
