package com.smartbox.investory.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.controllers.rest.ImportController;
import com.smartbox.investory.services.imports.ImportOrchestratorService;
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
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ImportOrchestratorService importOrchestratorService;

  @Test
  void unauthenticatedApiRequest_isUnauthorized() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "file.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

    mockMvc
        .perform(multipart("/import/broker/XTB").file(file).with(csrf()))
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
        .perform(multipart("/import/broker/XTB").file(file).with(csrf()))
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
        .perform(multipart("/import/broker/XTB").file(file).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void readEndpoint_requiresAuthenticationByDefault() throws Exception {
    mockMvc.perform(get("/dashboard")).andExpect(status().isUnauthorized());
  }
}
