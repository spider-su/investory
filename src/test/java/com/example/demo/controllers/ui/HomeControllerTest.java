package com.example.demo.controllers.ui;

import com.example.demo.services.BenchmarkService;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.Benchmark;
import com.example.demo.services.models.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Standalone setup: skips view rendering (the production Thymeleaf prefix points to
 * /static/dashboard/, while home.html lives under /templates/, so full rendering would fail
 * in tests). We only verify routing, model wiring and downstream service invocation.
 */
@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    @Mock private PortfolioService portfolioService;
    @Mock private BenchmarkService benchmarkService;

    @InjectMocks private HomeController homeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Stub view resolver: rewrites view names so MockMvc doesn't dispatch back to the same URL
        // (avoids "Circular view path" error in standalone setup without real templates).
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/WEB-INF/views/");
        resolver.setSuffix(".jsp");
        mockMvc = MockMvcBuilders.standaloneSetup(homeController)
                .setViewResolvers(resolver)
                .build();
    }

    @Test
    void rootReturnsHomeView() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));
    }

    @Test
    void dashboardPutsStatsAndBenchmarkInModel() throws Exception {
        when(portfolioService.calculateTotalProfitLoss()).thenReturn(new Portfolio());
        when(benchmarkService.calculate()).thenReturn(new Benchmark());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("stats"))
                .andExpect(model().attributeExists("benchmark"));
    }
}
