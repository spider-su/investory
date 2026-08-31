package com.smartbox.investory.retirement.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class RetirementPlanRestControllerIT {
  private RetirementPlanApi plans;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    plans = mock(RetirementPlanApi.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc =
        MockMvcBuilders.standaloneSetup(new RetirementPlanRestController(plans))
            .setValidator(validator)
            .build();
  }

  @Test
  void rejectsEmptyPlanWrite() throws Exception {
    mvc.perform(
            post("/api/v1/retirement/portfolios/7/plans")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(plans);
  }

  @Test
  void rejectsEmptyPlanUpdate() throws Exception {
    mvc.perform(
            put("/api/v1/retirement/portfolios/7/plans/9")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(plans);
  }

  @Test
  void rejectsEmptyEventWrite() throws Exception {
    mvc.perform(
            put("/api/v1/retirement/portfolios/7/plans/9/events/3")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(plans);
  }
}
