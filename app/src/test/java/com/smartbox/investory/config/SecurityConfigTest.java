package com.smartbox.investory.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.investment.api.importing.InvestmentImportApi;
import com.smartbox.investory.investment.web.ImportController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ImportController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
@DisplayName("Security Config")
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InvestmentImportApi importApi;

  @DisplayName("unauthenticated Api Request is Unauthorized")
  @Test
  void unauthenticatedApiRequest_isUnauthorized() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "file.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

    mockMvc
        .perform(multipart("/api/v1/investment/imports/broker/XTB").file(file).with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "user",
      roles = {"USER"})
  void userCannotImportBrokerFile() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "file.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

    mockMvc
        .perform(multipart("/api/v1/investment/imports/broker/XTB").file(file).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(
      username = "admin",
      roles = {"ADMIN", "USER"})
  void adminCanReachBrokerImportEndpoint() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "file.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

    mockMvc
        .perform(
            multipart("/api/v1/investment/imports/broker/XTB")
                .file(file)
                .param("portfolioId", "1")
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @DisplayName("read Endpoint requires Authentication By Default")
  @Test
  void readEndpoint_requiresAuthenticationByDefault() throws Exception {
    mockMvc.perform(get("/portfolios/1/dashboard")).andExpect(status().isUnauthorized());
  }

  @Test
  void readinessProbe_isOpen() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isNotFound());
  }

  @Test
  void livenessProbe_isOpen() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
  }
}
