package com.example.demo.controllers.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.demo.services.BenchmarkService;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.dashboard.DashboardPeriod;
import com.example.demo.services.dashboard.DashboardPeriodFilterService;
import com.example.demo.services.models.Benchmark;
import com.example.demo.services.models.InstrumentPerformance;
import com.example.demo.services.models.Portfolio;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Standalone setup: skips view rendering (the production Thymeleaf prefix points to
 * /static/dashboard/, while home.html lives under /templates/, so full rendering would fail in
 * tests). We only verify routing, model wiring and downstream service invocation.
 */
@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

  @Mock private PortfolioService portfolioService;
  @Mock private BenchmarkService benchmarkService;
  @Mock private DashboardPeriodFilterService dashboardPeriodFilterService;

  @InjectMocks private HomeController homeController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    when(dashboardPeriodFilterService.apply(any(Portfolio.class), any(DashboardPeriod.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Stub view resolver: rewrites view names so MockMvc doesn't dispatch back to the same URL
    // (avoids "Circular view path" error in standalone setup without real templates).
    InternalResourceViewResolver resolver = new InternalResourceViewResolver();
    resolver.setPrefix("/WEB-INF/views/");
    resolver.setSuffix(".jsp");
    mockMvc = MockMvcBuilders.standaloneSetup(homeController).setViewResolvers(resolver).build();
  }

  @Test
  void rootReturnsHomeView() throws Exception {
    mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("home"));
  }

  @Test
  void dashboardPutsStatsAndBenchmarkInModel() throws Exception {
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(new Portfolio());
    when(benchmarkService.calculate()).thenReturn(new Benchmark());

    mockMvc
        .perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(view().name("dashboard"))
        .andExpect(model().attributeExists("stats"))
        .andExpect(model().attributeExists("benchmark"));
  }

  @Test
  void dashboardLimitsTopGainersAndLosersToTen() throws Exception {
    Portfolio portfolio = new Portfolio();
    portfolio.setPerformancePerSymbol(performanceRows());
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(new Benchmark());

    mockMvc
        .perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("topGainers", Matchers.hasSize(10)))
        .andExpect(model().attribute("topLosers", Matchers.hasSize(10)))
        .andExpect(
            model()
                .attribute(
                    "topGainers",
                    Matchers.hasItem(Matchers.hasProperty("symbol", Matchers.equalTo("GAIN_12")))))
        .andExpect(
            model()
                .attribute(
                    "topLosers",
                    Matchers.hasItem(Matchers.hasProperty("symbol", Matchers.equalTo("LOSS_12")))));
  }

  private List<InstrumentPerformance> performanceRows() {
    List<InstrumentPerformance> rows = new ArrayList<>();
    for (int i = 1; i <= 12; i++) {
      rows.add(new InstrumentPerformance("GAIN_" + i, i, 0, i));
      rows.add(new InstrumentPerformance("LOSS_" + i, -i, 0, -i));
    }
    return rows;
  }
}
