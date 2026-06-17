package com.example.demo.controllers.rest;

import com.example.demo.config.MockMvcSecurityTestConfig;
import com.example.demo.config.SecurityConfig;
import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.ImportSourceType;
import com.example.demo.services.imports.ImportBatchResponse;
import com.example.demo.services.imports.ImportFailedException;
import com.example.demo.services.imports.ImportOrchestratorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImportController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class ImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ImportOrchestratorService importOrchestratorService;

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
    void importByBroker_unknownBrokerReturns400() throws Exception {
        MockMultipartFile multipart = new MockMultipartFile("file", "file.xlsx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

        mockMvc.perform(multipart("/import/broker/etoro").file(multipart).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void importByBroker_parserFailureReturns422() throws Exception {
        when(importOrchestratorService.importFile(any(), any(), any(), any(), any()))
                .thenThrow(new ImportFailedException("Failed to import (batchId=5): boom",
                        new IllegalStateException("boom")));

        MockMultipartFile multipart = new MockMultipartFile("file", "file.xlsx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

        mockMvc.perform(multipart("/import/broker/XTB").file(multipart).with(csrf()))
                .andExpect(status().is(422));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void legacyXtbEndpoint_delegatesToOrchestrator() throws Exception {
        when(importOrchestratorService.importFile(eq(BrokerType.XTB), any(), eq("legacy.xlsx"),
                eq(ImportSourceType.MANUAL), any()))
                .thenReturn(new ImportBatchResponse(1L, BrokerType.XTB, ImportBatchStatus.APPLIED,
                        1, 1, 0, "ok", false));

        MockMultipartFile multipart = new MockMultipartFile("file", "legacy.xlsx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

        mockMvc.perform(multipart("/import/xtb").file(multipart).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getBatch_returns404WhenMissing() throws Exception {
        when(importOrchestratorService.getBatch(404L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/import/batches/404"))
                .andExpect(status().isNotFound());
    }
}
