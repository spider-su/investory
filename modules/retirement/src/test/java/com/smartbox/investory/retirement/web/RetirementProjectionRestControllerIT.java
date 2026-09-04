package com.smartbox.investory.retirement.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class RetirementProjectionRestControllerIT {
  private RetirementProjectionApi projections;
  private RetirementPlanApi plans;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    projections = mock(RetirementProjectionApi.class);
    plans = mock(RetirementPlanApi.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc =
        MockMvcBuilders.standaloneSetup(new RetirementProjectionRestController(projections, plans))
            .setValidator(validator)
            .build();
  }

  @Test
  void rejectsIncompleteProjectionRequest() throws Exception {
    mvc.perform(
            post("/api/v1/retirement/portfolios/7/projections")
                .contentType("application/json")
                .content("{\"defaultCurrentAge\":-1}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(projections);
  }
}
