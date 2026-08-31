package com.smartbox.investory.investment.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi;
import com.smartbox.investory.investment.api.reporting.PerformanceAggregation;
import com.smartbox.investory.investment.api.reporting.PerformanceMetric;
import com.smartbox.investory.investment.api.reporting.PerformanceStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("Investment Performance Controller")
class InvestmentPerformanceControllerIT {
  private InvestmentPerformanceApi performance;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    performance = mock(InvestmentPerformanceApi.class);
    var conversionService = new DefaultFormattingConversionService();
    new InvestmentWebConversionConfig().addFormatters(conversionService);
    mvc =
        MockMvcBuilders.standaloneSetup(new InvestmentPerformanceController(performance))
            .setConversionService(conversionService)
            .build();
  }

  @DisplayName("performance Endpoints Normalize Account Ids And Defaults")
  @Test
  void performanceEndpointsNormalizeAccountIdsAndDefaults() throws Exception {
    mvc.perform(
            get("/api/v1/investment/performance/board")
                .param("accountIds", "1, 2")
                .param("aggregation", "annual")
                .param("metric", "profit")
                .param("style", "bars")
                .param("portfolioId", "1"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/investment/performance/monthly")
                .param("accountIds", "3,4")
                .param("portfolioId", "1"))
        .andExpect(status().isOk());
    verify(performance)
        .load(
            new InvestmentPerformanceApi.PerformanceBoardQuery(
                java.util.List.of(1L, 2L),
                PerformanceAggregation.ANNUAL,
                PerformanceMetric.PROFIT,
                PerformanceStyle.BARS,
                null,
                1L));
    verify(performance)
        .load(
            new InvestmentPerformanceApi.PerformanceBoardQuery(
                java.util.List.of(3L, 4L),
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.PROFIT,
                PerformanceStyle.BARS,
                null,
                1L));
  }

  @DisplayName("performance Endpoint Rejects Unknown Options")
  @Test
  void performanceEndpointRejectsUnknownOptions() throws Exception {
    mvc.perform(
            get("/api/v1/investment/performance/board")
                .param("metric", "yield")
                .param("portfolioId", "1"))
        .andExpect(status().isBadRequest());
  }
}
