package com.smartbox.investory.ui.investment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.investment.reconciliation.ReconciliationReport;
import com.smartbox.investory.investment.reconciliation.ReconciliationReportService;
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
            "RECONCILED",
            null,
            List.of(new ReconciliationReport.Checkpoint("C0", "files -> import history", 0, 0)),
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
