package com.example.demo.config;

import com.example.demo.infrastructure.BrokerType;
import com.example.demo.infrastructure.ImportBatchStatus;
import com.example.demo.infrastructure.ImportSourceType;
import com.example.demo.controllers.rest.ImportController;
import com.example.demo.controllers.rest.StockController;
import com.example.demo.services.MarketService;
import com.example.demo.services.imports.ImportBatchDetailsResponse;
import com.example.demo.services.imports.ImportOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ImportController.class, StockController.class})
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportOrchestratorService importOrchestratorService;

    @MockitoBean
    private MarketService marketService;

    @Test
    void unauthenticatedApiRequest_isUnauthorized() throws Exception {
        mockMvc.perform(get("/import/batches"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void userCanReadImportBatches() throws Exception {
        when(importOrchestratorService.listBatches(1)).thenReturn(List.of(
                new ImportBatchDetailsResponse(
                        1L,
                        BrokerType.XTB,
                        ImportSourceType.MANUAL,
                        null,
                        "sample.xlsx",
                        "hash",
                        ImportBatchStatus.APPLIED,
                        10,
                        10,
                        0,
                        "ok",
                        false,
                        ZonedDateTime.now(),
                        ZonedDateTime.now()
                )
        ));

        mockMvc.perform(get("/import/batches").param("limit", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void userCannotRunAdminPostEndpoint() throws Exception {
        mockMvc.perform(post("/stock/create"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
    void adminCanRunAdminPostEndpoint() throws Exception {
        mockMvc.perform(post("/stock/create"))
                .andExpect(status().isOk());

        verify(marketService).createStocks();
    }
}




