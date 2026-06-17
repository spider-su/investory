package com.example.demo.controllers.rest;

import com.example.demo.config.MockMvcSecurityTestConfig;
import com.example.demo.config.SecurityConfig;
import com.example.demo.services.indicators.FundamentalService;
import com.example.demo.services.indicators.TechnicalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IndicatorController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class IndicatorControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TechnicalService technicalService;
    @MockitoBean private FundamentalService fundamentalService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createTechnicals_invokesService() throws Exception {
        mockMvc.perform(post("/indicator/technical"))
                .andExpect(status().isOk());
        verify(technicalService).createTechnicalsFromStock();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateTechnicals_invokesService() throws Exception {
        mockMvc.perform(put("/indicator/technical"))
                .andExpect(status().isOk());
        verify(technicalService).updateTechnicals();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createFundamentals_invokesService() throws Exception {
        mockMvc.perform(post("/indicator/fundamental"))
                .andExpect(status().isOk());
        verify(fundamentalService).createFundamentalsFromStock();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateFundamentals_invokesService() throws Exception {
        mockMvc.perform(put("/indicator/fundamental"))
                .andExpect(status().isOk());
        verify(fundamentalService).updateFundamentals();
    }
}

