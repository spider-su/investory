package com.example.demo.controllers.rest.ghostfolio;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GhostfolioBenchmarkController.class)
class GhostfolioBenchmarkControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void returnsWatchlistEnvelope() throws Exception {
        mockMvc
                .perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watchlist", hasSize(0)));
    }

    @Test
    void returnsBenchmarkEnvelope() throws Exception {
        mockMvc
                .perform(get("/api/v1/benchmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchmarks", hasSize(11)))
                .andExpect(jsonPath("$.benchmarks[0].dataSource").value("4227afc8"))
                .andExpect(jsonPath("$.benchmarks[0].marketCondition").value("BEAR_MARKET"))
                .andExpect(jsonPath("$.benchmarks[0].name").value("Bitcoin"))
                .andExpect(jsonPath("$.benchmarks[0].performances.allTimeHigh.date").value("2025-10-06T00:00:00.000Z"))
                .andExpect(jsonPath("$.benchmarks[0].symbol").value("bitcoin"))
                .andExpect(jsonPath("$.benchmarks[0].trend50d").value("DOWN"))
                .andExpect(jsonPath("$.benchmarks[0].trend200d").value("DOWN"));
    }
}
