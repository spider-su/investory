package com.smartbox.investory.investment.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("Investment Dashboard Rest Controller")
class InvestmentDashboardRestControllerIT {
  private InvestmentDashboardApi dashboard;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    dashboard = mock(InvestmentDashboardApi.class);
    mvc = MockMvcBuilders.standaloneSetup(new InvestmentDashboardRestController(dashboard)).build();
  }

  @DisplayName("query And Kpi Endpoints Bind Requests")
  @Test
  void queryAndKpiEndpointsBindRequests() throws Exception {
    mvc.perform(
            post("/api/v1/investment/dashboard/query")
                .contentType("application/json")
                .content(
                    "{\"accountIds\":[],\"benchmarkAccountsSubmitted\":false,\"period\":\"YTD\",\"portfolioId\":1}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/investment/dashboard/performance-kpi").param("portfolioId", "1"))
        .andExpect(status().isOk());
    verify(dashboard).loadDashboard(any());
    verify(dashboard).loadPerformanceKpi(1L);
  }
}
