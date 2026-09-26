package com.smartbox.investory.ryczalt.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.integrations.management.api.IntegrationSettingsApi;
import com.smartbox.investory.ryczalt.application.onboarding.RyczaltOnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class RyczaltOnboardingRestControllerTest {
  private final RyczaltOnboardingService onboarding = mock(RyczaltOnboardingService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final IntegrationSettingsApi integrations = mock(IntegrationSettingsApi.class);
  private final Authentication authentication = mock(Authentication.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    when(authorization.canRead(7L, authentication)).thenReturn(true);
    when(authorization.canWrite(7L, authentication)).thenReturn(true);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new RyczaltOnboardingRestController(onboarding, authorization, integrations))
            .build();
  }

  @Test
  void exposesSupportedConfiguration() throws Exception {
    mvc.perform(get("/api/profiles/7/onboarding/configuration").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legalForm").value("JDG"))
        .andExpect(jsonPath("$.ryczaltRate").value("12%"))
        .andExpect(jsonPath("$.vatStatus").value("ACTIVE"));
  }

  @Test
  void rejectsLookupWithoutForwardingInvalidNip() throws Exception {
    when(onboarding.lookupCompany(7L, "123"))
        .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "nip_invalid"));
    mvc.perform(
            post("/api/profiles/7/onboarding/company/lookup")
                .principal(authentication)
                .contentType("application/json")
                .content("{\"nip\":\"123\"}"))
        .andExpect(status().isBadRequest());
    verify(onboarding).lookupCompany(7L, "123");
  }

  @Test
  void skipsKsefThroughTheOnboardingBoundary() throws Exception {
    mvc.perform(post("/api/profiles/7/onboarding/ksef/skip").principal(authentication))
        .andExpect(status().isOk());
    verify(onboarding).skipKsef(7L);
  }
}
