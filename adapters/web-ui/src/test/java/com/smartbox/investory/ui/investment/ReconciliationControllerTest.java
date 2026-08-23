package com.smartbox.investory.ui.investment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.investment.api.InvestmentReconciliationApi;
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

  @Mock private InvestmentReconciliationApi reconciliationApi;

  @InjectMocks private ReconciliationController reconciliationController;

  @Test
  void reconciliationReturnsReportView() throws Exception {
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(reconciliationController)
            .setViewResolvers(new InternalResourceViewResolver("/WEB-INF/views/", ".jsp"))
            .build();
    Object report = new Object();
    when(reconciliationApi.load("QUICK", null)).thenReturn(report);

    mockMvc
        .perform(get("/dashboard/reconciliation"))
        .andExpect(status().isOk())
        .andExpect(view().name("reconciliation"))
        .andExpect(model().attribute("report", report));

    verify(reconciliationApi).load("QUICK", null);
  }
}
