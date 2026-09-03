package com.smartbox.investory.investment.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.investment.api.reporting.InvestmentDailyPerformanceApi;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("Daily Performance Controller")
class DailyPerformanceControllerIT {
  private InvestmentDailyPerformanceApi performance;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    performance = mock(InvestmentDailyPerformanceApi.class);
    mvc = MockMvcBuilders.standaloneSetup(new DailyPerformanceController(performance)).build();
  }

  @DisplayName("attribution Binds Date And Account Set")
  @Test
  void attributionBindsDateAndAccountSet() throws Exception {
    mvc.perform(
            get("/api/v1/investment/performance/daily-attribution")
                .param("date", "2026-08-27")
                .param("portfolioId", "7")
                .param("accountIds", "1, 2,2"))
        .andExpect(status().isOk());
    verify(performance).load(7L, LocalDate.of(2026, 8, 27), Set.of(1L, 2L));
  }

  @Test
  void attributionRequiresPositivePortfolio() throws Exception {
    mvc.perform(
            get("/api/v1/investment/performance/daily-attribution")
                .param("date", "2026-08-27")
                .param("portfolioId", "0"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(performance);
  }
}
