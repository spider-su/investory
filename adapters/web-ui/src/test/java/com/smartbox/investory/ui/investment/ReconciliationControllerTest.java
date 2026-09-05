package com.smartbox.investory.ui.investment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationOverallState;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reconciliation Controller")
class ReconciliationControllerTest {

  @Mock private InvestmentReconciliationClient reconciliationApi;

  @InjectMocks private ReconciliationController reconciliationController;

  @DisplayName("reconciliation Returns Report View")
  @Test
  void reconciliationReturnsReportView() throws Exception {
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(reconciliationController)
            .setViewResolvers(new InternalResourceViewResolver("/WEB-INF/views/", ".jsp"))
            .build();
    ReconciliationReport report =
        new ReconciliationReport(
            ReconciliationOverallState.REVIEW,
            null,
            List.of(),
            List.of(),
            List.of(),
            Instant.parse("2026-08-27T10:00:00Z"),
            LocalDate.of(2026, 8, 27));
    when(reconciliationApi.loadReconciliationReport(7L)).thenReturn(report);

    mockMvc
        .perform(get("/portfolios/7/dashboard/reconciliation").param("portfolioId", "7"))
        .andExpect(status().isOk())
        .andExpect(view().name("reconciliation"))
        .andExpect(model().attribute("report", report));

    verify(reconciliationApi).loadReconciliationReport(7L);
  }

  @DisplayName("reconciliation Refresh Rebuilds Views And Redirects")
  @Test
  void reconciliationRefreshRebuildsViewsAndRedirects() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(reconciliationController).build();

    mockMvc
        .perform(post("/portfolios/7/dashboard/reconciliation/refresh").param("portfolioId", "7"))
        .andExpect(status().is3xxRedirection())
        .andExpect(view().name("redirect:/portfolios/7/dashboard/reconciliation?refreshed=true"));

    verify(reconciliationApi).refreshReconciliationViews();
  }
}
