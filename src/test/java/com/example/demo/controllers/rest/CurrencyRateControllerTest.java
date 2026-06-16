package com.example.demo.controllers.rest;

import com.example.demo.config.SecurityConfig;
import com.example.demo.services.CurrencyRateUpdaterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CurrencyRateController.class)
@Import(SecurityConfig.class)
class CurrencyRateControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CurrencyRateUpdaterService updaterService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void refresh_callsUpdater() throws Exception {
        mockMvc.perform(post("/currency/refresh"))
                .andExpect(status().isOk());

        verify(updaterService).updateCurrencyRates();
    }

    @Test
    @WithMockUser(roles = "USER")
    void refresh_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/currency/refresh"))
                .andExpect(status().isForbidden());
    }
}

