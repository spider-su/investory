package com.smartbox.investory.ui.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.investment.api.InvestmentDashboardApi;
import com.smartbox.investory.investment.reporting.dashboard.application.DashboardPageView;
import com.smartbox.investory.investment.reporting.dashboard.application.DashboardQuery;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriod;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

  @Mock private InvestmentDashboardApi dashboardFacade;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    HomeController controller = new HomeController(dashboardFacade);
    InternalResourceViewResolver resolver = new InternalResourceViewResolver();
    resolver.setPrefix("/WEB-INF/views/");
    resolver.setSuffix(".jsp");
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();
  }

  @Test
  void rootReturnsHomeView() throws Exception {
    mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("home"));
  }

  @Test
  void dashboardExposesOneDashboardPageViewAndPresentationMetadata() throws Exception {
    DashboardPageView dashboard = org.mockito.Mockito.mock(DashboardPageView.class);
    when(dashboard.selectedPeriod()).thenReturn(DashboardPeriod.ONE_YEAR);
    when(dashboard.periods()).thenReturn(List.of(DashboardPeriod.ONE_YEAR));
    when(dashboardFacade.loadDashboard(any())).thenReturn(dashboard);

    mockMvc
        .perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(view().name("dashboard"))
        .andExpect(model().attribute("dashboard", dashboard))
        .andExpect(model().attributeExists("selectedPeriod", "periods"));
  }

  @Test
  void dashboardRequestWithoutAnAccountSelectionUsesAllAccounts() throws Exception {
    stubDashboard();

    mockMvc.perform(get("/dashboard"));

    assertQuery(false, List.of(), null);
  }

  @Test
  void periodOnlyRequestUsesAllAccounts() throws Exception {
    stubDashboard();

    mockMvc.perform(get("/dashboard").param("period", "YTD"));

    assertQuery(false, List.of(), "YTD");
  }

  @Test
  void emptySubmittedAccountSelectionRemainsDefensive() throws Exception {
    stubDashboard();

    mockMvc.perform(
        get("/dashboard").param("period", "YTD").param("benchmarkAccountsSubmitted", "true"));

    assertQuery(true, List.of(), "YTD");
  }

  @Test
  void explicitAccountSubsetIsPassedToDashboard() throws Exception {
    stubDashboard();

    mockMvc.perform(
        get("/dashboard")
            .param("period", "YTD")
            .param("benchmarkAccountsSubmitted", "true")
            .param("accountIds", "1", "3"));

    assertQuery(true, List.of(1L, 3L), "YTD");
  }

  @Test
  void activePortfolioIdIsPassedToDashboard() throws Exception {
    stubDashboard();

    mockMvc.perform(get("/dashboard").param("portfolioId", "42"));

    ArgumentCaptor<DashboardQuery> query = ArgumentCaptor.forClass(DashboardQuery.class);
    verify(dashboardFacade).loadDashboard(query.capture());
    assertEquals(42L, query.getValue().portfolioId());
  }

  private void stubDashboard() {
    DashboardPageView dashboard = org.mockito.Mockito.mock(DashboardPageView.class);
    when(dashboard.selectedPeriod()).thenReturn(DashboardPeriod.YEAR_TO_DATE);
    when(dashboard.periods()).thenReturn(List.of(DashboardPeriod.YEAR_TO_DATE));
    when(dashboardFacade.loadDashboard(any())).thenReturn(dashboard);
  }

  private void assertQuery(boolean submitted, List<Long> accountIds, String period) {
    ArgumentCaptor<DashboardQuery> query = ArgumentCaptor.forClass(DashboardQuery.class);
    verify(dashboardFacade).loadDashboard(query.capture());
    assertEquals(submitted, query.getValue().benchmarkAccountsSubmitted());
    assertEquals(accountIds, query.getValue().accountIds());
    assertEquals(period, query.getValue().period());
    assertEquals(1L, query.getValue().portfolioId());
  }
}
