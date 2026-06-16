package com.example.demo.controllers.rest;

import com.example.demo.config.MockMvcSecurityTestConfig;
import com.example.demo.config.SecurityConfig;
import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;
import com.example.demo.services.MarketService;
import com.example.demo.services.imports.ImportBatchResponse;
import com.example.demo.services.imports.ImportOrchestratorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImportController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class ImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ImportOrchestratorService importOrchestratorService;
    @MockitoBean private MarketService marketService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void importByBroker_uploadsFileAndReturnsResponse() throws Exception {
        when(importOrchestratorService.importFile(eq(BrokerType.XTB), any(), eq("file.xlsx"),
                eq(ImportSourceType.MANUAL), any()))
                .thenReturn(new ImportBatchResponse(99L, BrokerType.XTB, ImportBatchStatus.APPLIED,
                        10, 10, 0, "ok", false));

        MockMultipartFile multipart = new MockMultipartFile("file", "file.xlsx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

        mockMvc.perform(multipart("/import/broker/XTB").file(multipart).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(99))
                .andExpect(jsonPath("$.duplicate").value(false));

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(importOrchestratorService).importFile(eq(BrokerType.XTB), bytesCaptor.capture(),
                eq("file.xlsx"), eq(ImportSourceType.MANUAL), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void importByBroker_failsOnUnknownBroker() {
        MockMultipartFile multipart = new MockMultipartFile("file", "file.xlsx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

        // The controller wraps unknown-broker errors in a RuntimeException that bubbles up through
        // MockMvc (no @ExceptionHandler is registered). Assert that propagation, which proves the
        // request reached the controller and the unsupported broker was rejected.
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                mockMvc.perform(multipart("/import/broker/etoro").file(multipart).with(csrf())));
    }
}

