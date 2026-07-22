package com.example.demo.controllers.rest.ghostfolio;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GhostfolioHoldingsController.class)
class GhostfolioHoldingsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GhostfolioCompatibilityService compatibilityService;

    @Test
    void returnsHoldingsListWithAssetProfile() throws Exception {
        when(compatibilityService.holdings("1", "AAPL.US"))
                .thenReturn(
                        Map.of(
                                "holdings",
                                List.of(
                                        Map.of(
                                                "symbol", "AAPL.US",
                                                "assetProfile",
                                                        Map.of(
                                                                "assetClass", "EQUITY",
                                                                "assetSubClass", "STOCK",
                                                                "currency", "USD"),
                                                "valueInBaseCurrency", 2000.0d))));

        mockMvc
                .perform(
                        get("/api/v1/portfolio/holdings")
                                .queryParam("accounts", "1")
                                .queryParam("symbol", "AAPL.US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings", hasSize(1)))
                .andExpect(jsonPath("$.holdings[0].symbol").value("AAPL.US"))
                .andExpect(jsonPath("$.holdings[0].assetProfile.assetClass").value("EQUITY"))
                .andExpect(jsonPath("$.holdings[0].assetProfile.assetSubClass").value("STOCK"))
                .andExpect(jsonPath("$.holdings[0].assetProfile.currency").value("USD"))
                .andExpect(jsonPath("$.holdings[0].valueInBaseCurrency").value(2000.0));
    }
}
