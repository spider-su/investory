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

@WebMvcTest(GhostfolioPortfolioController.class)
class GhostfolioPortfolioControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GhostfolioCompatibilityService compatibilityService;

    @Test
    void returnsGhostfolioPerformanceEnvelope() throws Exception {
        when(compatibilityService.performance("1", "max"))
                .thenReturn(
                        Map.of(
                                "performance",
                                        Map.of(
                                                "currentValue", 157_972.0d,
                                                "currentGrossPerformance", 8_160.0d,
                                                "currentNetPerformance", 5_983.0d),
                                "chart",
                                        List.of(Map.of("date", "2026-07-01T00:00:00Z")),
                                "benchmark",
                                        List.of(),
                                "hasError",
                                        false));

        mockMvc
                .perform(
                        get("/api/v2/portfolio/performance")
                                .queryParam("range", "max")
                                .queryParam("accounts", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performance.currentValue").value(157972.0))
                .andExpect(jsonPath("$.performance.currentGrossPerformance").value(8160.0))
                .andExpect(jsonPath("$.performance.currentNetPerformance").value(5983.0))
                .andExpect(jsonPath("$.chart", hasSize(1)))
                .andExpect(jsonPath("$.benchmark", hasSize(0)))
                .andExpect(jsonPath("$.hasError").value(false));
    }
}
