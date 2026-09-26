package com.smartbox.investory.ryczalt.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.onboarding.RyczaltReadinessService;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RyczaltAccountingReadinessRestControllerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final RyczaltReadinessService readiness = mock(RyczaltReadinessService.class);
  private final Authentication authentication = mock(Authentication.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    when(authorization.canRead(7L, authentication)).thenReturn(true);
    when(authorization.canWrite(7L, authentication)).thenReturn(true);
    mvc = MockMvcBuilders.standaloneSetup(new RyczaltAccountingReadinessRestController(authorization, readiness)).build();
  }

  @Test
  void rejectsReadForAnotherProfile() throws Exception {
    when(authorization.canRead(8L, authentication)).thenReturn(false);
    mvc.perform(get("/api/profiles/8/accounting/readiness?month=2026-09").principal(authentication))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsUnsupportedActivityConfirmation() throws Exception {
    mvc.perform(post("/api/profiles/7/accounting/periods/2026-09/activity-confirmation")
            .principal(authentication).contentType("application/json").content("{\"type\":\"NO_COSTS\"}"))
        .andExpect(status().isBadRequest());
  }
}
