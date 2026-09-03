package com.smartbox.investory.investment.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationOverallState;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("Investment Reconciliation Rest Controller")
class InvestmentReconciliationRestControllerIT {
  private InvestmentReconciliationApi reconciliation;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    reconciliation = mock(InvestmentReconciliationApi.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new InvestmentReconciliationRestController(reconciliation))
            .build();
  }

  @DisplayName("report Loads Portfolio Diagnostic")
  @Test
  void reportLoadsPortfolioDiagnostic() throws Exception {
    when(reconciliation.loadReconciliationReport(7L))
        .thenReturn(
            new ReconciliationReport(
                ReconciliationOverallState.REVIEW,
                null,
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-08-29T10:00:00Z"),
                LocalDate.of(2026, 8, 29)));

    mvc.perform(get("/api/v1/investment/reconciliation").param("portfolioId", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overallState").value("REVIEW"))
        .andExpect(jsonPath("$.checkpoints").isArray())
        .andExpect(jsonPath("$.issueGroups").isArray())
        .andExpect(jsonPath("$.issues").isArray())
        .andExpect(jsonPath("$.asOfDate").value("2026-08-29"));
    verify(reconciliation).loadReconciliationReport(7L);
  }

  @Test
  void reportRequiresPortfolioId() throws Exception {
    mvc.perform(get("/api/v1/investment/reconciliation")).andExpect(status().isBadRequest());
    verifyNoInteractions(reconciliation);
  }
}
