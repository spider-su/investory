package com.example.demo.controllers.rest.ghostfolio;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
  GhostfolioAuthController.class,
  GhostfolioUserController.class,
  GhostfolioInfoController.class,
  GhostfolioHealthController.class,
  GhostfolioAssetsController.class
})
class GhostfolioBootstrapControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GhostfolioCompatibilityService compatibilityService;

  @Test
  void returnsAnonymousAuthEnvelope() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/anonymous").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists())
        .andExpect(jsonPath("$.expiresIn").value(86400))
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
  }

  @Test
  void returnsCurrentUserWithReadOnlyPermissionsAndInvestoryAccounts() throws Exception {
    when(compatibilityService.accounts())
        .thenReturn(
            Map.of("accounts", List.of(Map.of("id", "1", "name", "IBKR")), "activitiesCount", 4));

    mockMvc
        .perform(get("/api/v1/user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions", hasSize(1)))
        .andExpect(jsonPath("$.permissions[0]").value("read"))
        .andExpect(jsonPath("$.accounts", hasSize(1)))
        .andExpect(jsonPath("$.accounts[0].name").value("IBKR"))
        .andExpect(jsonPath("$.activitiesCount").value(4))
        .andExpect(jsonPath("$.settings.baseCurrency").value("USD"));
  }

  @Test
  void returnsUserSettingsInfoHealthAndManifest() throws Exception {
    mockMvc
        .perform(get("/api/v1/user/settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settings.baseCurrency").value("USD"));
    mockMvc
        .perform(get("/api/v1/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platform").value("Investory"))
        .andExpect(jsonPath("$.features.authToken").value(true));
    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"));
    mockMvc
        .perform(get("/api/assets/en/site.webmanifest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Investory"))
        .andExpect(jsonPath("$.icons", hasSize(2)));
  }
}
