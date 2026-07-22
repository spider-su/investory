package com.example.demo.controllers.rest.ghostfolio;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
    GhostfolioPortfolioDetailsController.class,
    GhostfolioAccountController.class,
    GhostfolioHoldingsController.class
})
class GhostfolioPortfolioDetailsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GhostfolioCompatibilityService compatibilityService;

    @Test
    void returnsGhostfolioPortfolioDetailsContract() throws Exception {
        when(compatibilityService.details(null, null))
                .thenReturn(
                        Map.of(
                                "accounts",
                                        Map.of(
                                                "1",
                                                Map.of(
                                                        "name", "Broker USD",
                                                        "valueInBaseCurrency", 10000.0d)),
                                "holdings",
                                        Map.of(
                                                "AAPL.US",
                                                Map.of(
                                                        "quantity", 10.0d,
                                                        "assetProfile", Map.of("name", "AAPL.US"),
                                                        "valueInBaseCurrency", 8000.0d,
                                                        "investment", 7000.0d,
                                                        "netPerformance", 1000.0d)),
                                "platforms",
                                        Map.of("1", Map.of("valueInBaseCurrency", 10000.0d)),
                                "summary",
                                        Map.of(
                                                "currentValueInBaseCurrency", 10000.0d,
                                                "totalInvestment", 8000.0d,
                                                "grossPerformance", 2000.0d,
                                                "netPerformance", 1900.0d),
                                "createdAt",
                                        "2026-07-01T00:00:00Z"));

        mockMvc
                .perform(get("/api/v1/portfolio/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.1.name").value("Broker USD"))
                .andExpect(jsonPath("$.accounts.1.valueInBaseCurrency").value(10000.0))
                .andExpect(jsonPath("$.holdings['AAPL.US'].quantity").value(10.0))
                .andExpect(
                        jsonPath("$.holdings['AAPL.US'].assetProfile.name").value("AAPL.US"))
                .andExpect(
                        jsonPath("$.holdings['AAPL.US'].valueInBaseCurrency")
                                .value(8000.0))
                .andExpect(jsonPath("$.holdings['AAPL.US'].investment").value(7000.0))
                .andExpect(jsonPath("$.holdings['AAPL.US'].netPerformance").value(1000.0))
                .andExpect(jsonPath("$.platforms.1.valueInBaseCurrency").value(10000.0))
                .andExpect(jsonPath("$.summary.currentValueInBaseCurrency").value(10000.0))
                .andExpect(jsonPath("$.summary.totalInvestment").value(8000.0))
                .andExpect(jsonPath("$.summary.grossPerformance").value(2000.0))
                .andExpect(jsonPath("$.summary.netPerformance").value(1900.0));
    }

    @Test
    void filtersHoldingsAndAccounts() throws Exception {
        when(compatibilityService.details("1", "AAPL.US"))
                .thenReturn(
                        Map.of(
                                "accounts", Map.of("1", Map.of("name", "One")),
                                "holdings", Map.of("AAPL.US", Map.of("symbol", "AAPL.US")),
                                "platforms", Map.of(),
                                "summary", Map.of(),
                                "createdAt", "2026-07-01T00:00:00Z"));

        mockMvc
                .perform(
                        get("/api/v1/portfolio/details")
                                .queryParam("accounts", "1")
                                .queryParam("symbol", "AAPL.US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.1").exists())
                .andExpect(jsonPath("$.accounts.2").doesNotExist())
                .andExpect(jsonPath("$.holdings['AAPL.US']").exists())
                .andExpect(jsonPath("$.holdings['MSFT.US']").doesNotExist());
    }
}
