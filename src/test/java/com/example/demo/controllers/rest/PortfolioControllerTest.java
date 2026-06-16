package com.example.demo.controllers.rest;

import com.example.demo.config.MockMvcSecurityTestConfig;
import com.example.demo.config.SecurityConfig;
import com.example.demo.data.CurrencyType;
import com.example.demo.services.BenchmarkService;
import com.example.demo.services.HistoryService;
import com.example.demo.services.MarketService;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.Benchmark;
import com.example.demo.services.models.Portfolio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PortfolioController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class PortfolioControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PortfolioService portfolioService;
    @MockitoBean private HistoryService historyService;
    @MockitoBean private MarketService marketService;
    @MockitoBean private BenchmarkService benchmarkService;

    @Test
    @WithMockUser(roles = "USER")
    void getTotalProfitLoss_returnsPortfolio() throws Exception {
        Portfolio portfolio = new Portfolio();
        portfolio.setBalance(12345.0);
        portfolio.setTotal(678.0);
        when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);

        mockMvc.perform(get("/portfolio/total-profit-loss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(12345.0))
                .andExpect(jsonPath("$.total").value(678.0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getBenchmark_returnsBenchmark() throws Exception {
        Benchmark benchmark = new Benchmark();
        benchmark.setAvailable(true);
        when(benchmarkService.calculate()).thenReturn(benchmark);

        mockMvc.perform(get("/portfolio/benchmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getWinRate_returnsDouble() throws Exception {
        when(portfolioService.calculateWinRate()).thenReturn(66.6);

        mockMvc.perform(get("/portfolio/win-rate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getLargestWinLoss_returnsMap() throws Exception {
        when(portfolioService.calculateLargestWinLoss()).thenReturn(Map.of(
                "largestWin", 100.0,
                "largestLoss", -50.0
        ));

        mockMvc.perform(get("/portfolio/largest-win-loss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.largestWin").value(100.0))
                .andExpect(jsonPath("$.largestLoss").value(-50.0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getPerformancePerInstrument_passesBaseCurrencyParam() throws Exception {
        when(portfolioService.calculatePerformancePerInstrument(CurrencyType.USD)).thenReturn(List.of());

        mockMvc.perform(get("/portfolio/performance-per-instrument").param("baseCurrency", "USD"))
                .andExpect(status().isOk());

        verify(portfolioService).calculatePerformancePerInstrument(CurrencyType.USD);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sync_runsFullPortfolioUpdate() throws Exception {
        mockMvc.perform(post("/portfolio/sync"))
                .andExpect(status().isOk());

        verify(marketService).fullPortfolioUpdate();
    }

    @Test
    @WithMockUser(roles = "USER")
    void sync_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/portfolio/sync"))
                .andExpect(status().isForbidden());
    }
}

