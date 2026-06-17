package com.example.demo.controllers.rest;

import com.example.demo.config.MockMvcSecurityTestConfig;
import com.example.demo.config.SecurityConfig;
import com.example.demo.services.MarketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StockController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class StockControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MarketService marketService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_invokesMarketServiceCreateStocks() throws Exception {
        mockMvc.perform(post("/stock/create").with(csrf()))
                .andExpect(status().isOk());
        verify(marketService).createStocks();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sync_invokesMarketServiceUpdateStocks() throws Exception {
        mockMvc.perform(post("/stock/sync").with(csrf()))
                .andExpect(status().isOk());
        verify(marketService).updateStocks();
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminCannotPost() throws Exception {
        mockMvc.perform(post("/stock/create").with(csrf()))
                .andExpect(status().isForbidden());
    }
}

