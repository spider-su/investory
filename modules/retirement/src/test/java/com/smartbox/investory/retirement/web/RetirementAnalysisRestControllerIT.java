package com.smartbox.investory.retirement.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.retirement.api.RetirementAnalysisApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.RetirementAnalysisResult;
import com.smartbox.investory.retirement.api.model.SimulationChartData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("Retirement Analysis Rest Controller")
class RetirementAnalysisRestControllerIT {
  private RetirementAnalysisApi analyses;
  private RetirementProjectionApi projections;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    analyses = mock(RetirementAnalysisApi.class);
    projections = mock(RetirementProjectionApi.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new RetirementAnalysisRestController(analyses, projections))
            .build();
    when(analyses.analyze(any()))
        .thenReturn(
            RetirementAnalysisResult.noForwardHorizon(
                new SimulationChartData(Map.of(), List.of(), List.of())));
  }

  @DisplayName("analyze Binds Projection Body")
  @Test
  void analyzeBindsProjectionBody() throws Exception {
    mvc.perform(
            post("/api/v1/retirement/portfolios/7/analysis")
                .contentType("application/json")
                .content("{\"defaultCurrentAge\":40,\"defaultEndAge\":95}"))
        .andExpect(status().isOk());
    verify(projections).load(eq(7L), isNull(), eq(40), eq(95));
    verify(analyses).analyze(any());
  }
}
