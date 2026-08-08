package com.example.demo.controllers.ui;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.demo.services.reconciliation.ReconciliationReport;
import com.example.demo.services.reconciliation.ReconciliationReport.AccountSummary;
import com.example.demo.services.reconciliation.ReconciliationReport.PositionSummary;
import com.example.demo.services.reconciliation.ReconciliationReportService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@ExtendWith(MockitoExtension.class)
class ReconciliationControllerTest {

  @Mock private ReconciliationReportService reconciliationReportService;

  @InjectMocks private ReconciliationController reconciliationController;

  @Test
  void reconciliationReturnsReportView() throws Exception {
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(reconciliationController)
            .setViewResolvers(new InternalResourceViewResolver("/WEB-INF/views/", ".jsp"))
            .build();
    ReconciliationReport report =
        new ReconciliationReport(
            new PositionSummary(2, 1, 1, 1, 1),
            new AccountSummary(1, 1, 0, 1),
            List.of(),
            List.of());
    when(reconciliationReportService.load()).thenReturn(report);

    mockMvc
        .perform(get("/dashboard/reconciliation"))
        .andExpect(status().isOk())
        .andExpect(view().name("reconciliation"))
        .andExpect(model().attribute("report", report));

    verify(reconciliationReportService).load();
  }
}
