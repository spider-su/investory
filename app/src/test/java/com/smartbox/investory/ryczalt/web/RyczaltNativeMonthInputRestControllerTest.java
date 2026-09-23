package com.smartbox.investory.ryczalt.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.RyczaltNativeMonthInputService;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RyczaltNativeMonthInputRestControllerTest {
  private final RyczaltNativeMonthInputService inputs = mock(RyczaltNativeMonthInputService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final Authentication authentication = mock(Authentication.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    when(authorization.canWrite(7L, authentication)).thenReturn(true);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new RyczaltNativeMonthInputRestController(inputs, authorization))
            .build();
  }

  @Test
  void savesSettingsForAuthorizedProfileAndMonth() throws Exception {
    mvc.perform(
            put("/api/profiles/7/accounting/periods/2026-09/input-settings")
                .principal(authentication)
                .contentType("application/json")
                .content(
                    "{\"jdgActive\":true,\"qualifyingUop\":false,"
                        + "\"zusRegime\":\"JDG\",\"voluntarySickness\":false,"
                        + "\"ytdRyczaltRevenue\":\"10000\",\"fullJdgSocial\":\"2000\","
                        + "\"deductionsAlreadyConsumed\":\"0\","
                        + "\"salesCorrections\":\"0\",\"explicitVatAdjustments\":\"0\"}"))
        .andExpect(status().isNoContent());

    verify(inputs).save(eq(7L), eq(YearMonth.of(2026, 9)), any());
  }
}
