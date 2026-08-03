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

@WebMvcTest(GhostfolioActivitiesController.class)
class GhostfolioActivitiesControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GhostfolioCompatibilityService compatibilityService;

  @Test
  void forwardsFiltersSortingAndPagination() throws Exception {
    when(compatibilityService.activities("1", "BUY", "1y", "AAPL.US", "date", "asc", 25, 50))
        .thenReturn(
            Map.of(
                "activities",
                List.of(
                    Map.of(
                        "id", "OPEN:1",
                        "symbol", "AAPL.US",
                        "type", "BUY",
                        "date", "2026-07-01T00:00:00Z")),
                "count",
                100));

    mockMvc
        .perform(
            get("/api/v1/activities")
                .queryParam("accounts", "1")
                .queryParam("activityTypes", "BUY")
                .queryParam("range", "1y")
                .queryParam("symbol", "AAPL.US")
                .queryParam("sortColumn", "date")
                .queryParam("sortDirection", "asc")
                .queryParam("take", "25")
                .queryParam("skip", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activities", hasSize(1)))
        .andExpect(jsonPath("$.activities[0].type").value("BUY"))
        .andExpect(jsonPath("$.count").value(100));
  }
}
