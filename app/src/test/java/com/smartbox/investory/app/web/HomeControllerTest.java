package com.smartbox.investory.ui.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi.DashboardPageView;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi.DashboardQuery;
import com.smartbox.investory.ui.investment.InvestmentDashboardClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@ExtendWith(MockitoExtension.class)
@DisplayName("Home Controller")
class HomeControllerTest {

  @Mock private InvestmentDashboardClient investmentDashboard;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    HomeController controller = new HomeController(investmentDashboard);
    InternalResourceViewResolver resolver = new InternalResourceViewResolver();
    resolver.setPrefix("/WEB-INF/views/");
    resolver.setSuffix(".jsp");
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();
  }

  @DisplayName("root Returns Home View")
  @Test
  void rootReturnsHomeView() throws Exception {
    mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("home"));
  }

  @DisplayName("dashboard Exposes One Dashboard Page View And Presentation Metadata")
  @Test
  void dashboardExposesOneDashboardPageViewAndPresentationMetadata() throws Exception {
    DashboardPageView dashboard = org.mockito.Mockito.mock(DashboardPageView.class);
    when(dashboard.selectedPeriod()).thenReturn(DashboardPeriod.ONE_YEAR);
    when(dashboard.periods()).thenReturn(List.of(DashboardPeriod.ONE_YEAR));
    when(investmentDashboard.loadDashboard(any())).thenReturn(dashboard);

    mockMvc
        .perform(get("/dashboard").param("portfolioId", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("dashboard"))
        .andExpect(model().attribute("dashboard", dashboard))
        .andExpect(model().attributeExists("selectedPeriod", "periods"));
  }

  @DisplayName("dashboard Request Without An Account Selection Uses All Accounts")
  @Test
  void dashboardRequestWithoutAnAccountSelectionUsesAllAccounts() throws Exception {
    stubDashboard();

    mockMvc.perform(get("/dashboard").param("portfolioId", "1"));

    assertQuery(false, List.of(), DashboardPeriod.YEAR_TO_DATE);
  }

  @DisplayName("period Only Request Uses All Accounts")
  @Test
  void periodOnlyRequestUsesAllAccounts() throws Exception {
    stubDashboard();

    mockMvc.perform(get("/dashboard").param("portfolioId", "1").param("period", "YTD"));

    assertQuery(false, List.of(), DashboardPeriod.YEAR_TO_DATE);
  }

  @DisplayName("empty Submitted Account Selection Remains Defensive")
  @Test
  void emptySubmittedAccountSelectionRemainsDefensive() throws Exception {
    stubDashboard();

    mockMvc.perform(
        get("/dashboard")
            .param("portfolioId", "1")
            .param("period", "YTD")
            .param("benchmarkAccountsSubmitted", "true"));

    assertQuery(true, List.of(), DashboardPeriod.YEAR_TO_DATE);
  }

  @DisplayName("explicit Account Subset Is Passed To Dashboard")
  @Test
  void explicitAccountSubsetIsPassedToDashboard() throws Exception {
    stubDashboard();

    mockMvc.perform(
        get("/dashboard")
            .param("portfolioId", "1")
            .param("period", "YTD")
            .param("benchmarkAccountsSubmitted", "true")
            .param("accountIds", "1", "3"));

    assertQuery(true, List.of(1L, 3L), DashboardPeriod.YEAR_TO_DATE);
  }

  @DisplayName("active Portfolio Id Is Passed To Dashboard")
  @Test
  void activePortfolioIdIsPassedToDashboard() throws Exception {
    stubDashboard();

    mockMvc.perform(get("/dashboard").param("portfolioId", "42"));

    ArgumentCaptor<DashboardQuery> query = ArgumentCaptor.forClass(DashboardQuery.class);
    verify(investmentDashboard).loadDashboard(query.capture());
    assertEquals(42L, query.getValue().portfolioId());
  }

  private void stubDashboard() {
    DashboardPageView dashboard = org.mockito.Mockito.mock(DashboardPageView.class);
    when(dashboard.selectedPeriod()).thenReturn(DashboardPeriod.YEAR_TO_DATE);
    when(dashboard.periods()).thenReturn(List.of(DashboardPeriod.YEAR_TO_DATE));
    when(investmentDashboard.loadDashboard(any())).thenReturn(dashboard);
  }

  private void assertQuery(boolean submitted, List<Long> accountIds, DashboardPeriod period) {
    ArgumentCaptor<DashboardQuery> query = ArgumentCaptor.forClass(DashboardQuery.class);
    verify(investmentDashboard).loadDashboard(query.capture());
    assertEquals(submitted, query.getValue().benchmarkAccountsSubmitted());
    assertEquals(accountIds, query.getValue().accountIds());
    assertEquals(period, query.getValue().period());
    assertEquals(1L, query.getValue().portfolioId());
  }
}
