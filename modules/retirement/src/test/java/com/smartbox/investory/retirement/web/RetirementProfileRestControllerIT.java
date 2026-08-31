package com.smartbox.investory.retirement.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.retirement.api.RetirementProfileApi;
import com.smartbox.investory.retirement.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("Retirement Profile Rest Controller")
class RetirementProfileRestControllerIT {
  private RetirementProfileApi profile;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    profile = mock(RetirementProfileApi.class);
    mvc = MockMvcBuilders.standaloneSetup(new RetirementProfileRestController(profile)).build();
  }

  @DisplayName("annual Cost Binds Portfolio And Currency")
  @Test
  void annualCostBindsPortfolioAndCurrency() throws Exception {
    mvc.perform(
            get("/api/v1/retirement/profile/annual-cost")
                .param("portfolioId", "7")
                .param("reportingCurrency", "EUR"))
        .andExpect(status().isOk());
    verify(profile)
        .currentYearAnnualCost(7L, com.smartbox.investory.shared.currency.CurrencyType.EUR);
  }
}
