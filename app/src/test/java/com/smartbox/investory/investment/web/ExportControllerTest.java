package com.smartbox.investory.investment.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.MockMvcSecurityTestConfig;
import com.smartbox.investory.config.RestApiExceptionHandler;
import com.smartbox.investory.config.SecurityConfig;
import com.smartbox.investory.investment.api.exporting.YahooPortfolioExportApi;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExportController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class, RestApiExceptionHandler.class})
@DisplayName("Export Controller")
class ExportControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private YahooPortfolioExportApi exportService;

  // ── GET /api/v1/investment/export/generate ─────────────────────────────

  @DisplayName("generate returns Portfolio Csv Download")
  @Test
  @WithMockUser
  void generate_returnsPortfolioCsvDownload() throws Exception {
    doAnswer(
            invocation -> {
              String path = invocation.getArgument(1, String.class);
              Files.writeString(Path.of(path), "Symbol\nAAPL", StandardCharsets.UTF_8);
              return null;
            })
        .when(exportService)
        .exportToYahooCsv(eq(1L), anyString());

    mockMvc
        .perform(get("/api/v1/investment/export/generate").param("portfolioId", "1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv"))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(header().string("Content-Disposition", containsString("yahoo-portfolio-")))
        .andExpect(content().string(containsString("AAPL")));
  }

  @DisplayName("generate requires Read Authentication")
  @Test
  void generate_requiresReadAuthentication() throws Exception {
    doAnswer(
            invocation -> {
              String path = invocation.getArgument(1, String.class);
              Files.writeString(Path.of(path), "Symbol\nAAPL", StandardCharsets.UTF_8);
              return null;
            })
        .when(exportService)
        .exportToYahooCsv(eq(1L), anyString());

    mockMvc.perform(get("/api/v1/investment/export/generate")).andExpect(status().isUnauthorized());
  }

  @DisplayName("generate Uses Standard Error Contract")
  @Test
  @WithMockUser
  void generate_usesStandardErrorContract() throws Exception {
    doThrow(new java.io.IOException("disk detail"))
        .when(exportService)
        .exportToYahooCsv(eq(1L), anyString());

    mockMvc
        .perform(get("/api/v1/investment/export/generate").param("portfolioId", "1"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType("application/json"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status")
                .value(500))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                .value("Internal server error"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.path")
                .value("/api/v1/investment/export/generate"));
  }
}
