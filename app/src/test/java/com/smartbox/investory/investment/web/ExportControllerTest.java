package com.smartbox.investory.ui.investment;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.MockMvcSecurityTestConfig;
import com.smartbox.investory.config.SecurityConfig;
import com.smartbox.investory.integrations.infrastructure.integration.export.yahoo.YahooExportService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExportController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class ExportControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private YahooExportService exportService;

  // ── GET /export/generate ────────────────────────────────────────────────

  @Test
  @WithMockUser
  void generate_returnsPortfolioCsvDownload() throws Exception {
    doAnswer(
            invocation -> {
              String path = invocation.getArgument(0, String.class);
              Files.writeString(Path.of(path), "Symbol\nAAPL", StandardCharsets.UTF_8);
              return null;
            })
        .when(exportService)
        .exportToYahooCsv(anyString());

    mockMvc
        .perform(get("/export/generate"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv"))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(header().string("Content-Disposition", containsString("yahoo-portfolio-")))
        .andExpect(content().string(containsString("AAPL")));
  }

  @Test
  void generate_requiresReadAuthentication() throws Exception {
    doAnswer(
            invocation -> {
              String path = invocation.getArgument(0, String.class);
              Files.writeString(Path.of(path), "Symbol\nAAPL", StandardCharsets.UTF_8);
              return null;
            })
        .when(exportService)
        .exportToYahooCsv(anyString());

    mockMvc.perform(get("/export/generate")).andExpect(status().isUnauthorized());
  }
}
