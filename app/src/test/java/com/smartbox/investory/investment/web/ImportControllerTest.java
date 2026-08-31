package com.smartbox.investory.ui.investment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.MockMvcSecurityTestConfig;
import com.smartbox.investory.config.SecurityConfig;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi.ImportResult;
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

@WebMvcTest(controllers = ImportController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class ImportControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private InvestmentImportApi importApi;

  @Test
  @WithMockUser(roles = "ADMIN")
  void importByBroker_uploadsFileAndReturnsResponse() throws Exception {
    when(importApi.importForBroker(eq("XTB"), eq("file.xlsx"), any(), eq("MANUAL"), any()))
        .thenReturn(new ImportResult(99L, "XTB", "COMPLETED", 10, 10, 0, "ok", false));

    MockMultipartFile multipart =
        new MockMultipartFile(
            "file", "file.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

    mockMvc
        .perform(multipart("/import/broker/XTB").file(multipart).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.batchId").value(99))
        .andExpect(jsonPath("$.duplicate").value(false));

    ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(importApi)
        .importForBroker(eq("XTB"), eq("file.xlsx"), bytesCaptor.capture(), eq("MANUAL"), any());
    org.junit.jupiter.api.Assertions.assertArrayEquals(
        "payload".getBytes(), bytesCaptor.getValue());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void importByBroker_passesSourceMetadata() throws Exception {
    when(importApi.importForBroker(
            eq("IBKR"), eq("statement.csv"), any(), eq("TELEGRAM"), eq("telegram-file-123")))
        .thenReturn(new ImportResult(100L, "IBKR", "COMPLETED", 1, 1, 0, "ok", false));

    MockMultipartFile multipart =
        new MockMultipartFile(
            "file", "statement.csv", MediaType.TEXT_PLAIN_VALUE, "payload".getBytes());

    mockMvc
        .perform(
            multipart("/import/broker/ibkr")
                .file(multipart)
                .param("source", "TELEGRAM")
                .param("sourceRef", "telegram-file-123")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.broker").value("IBKR"));

    verify(importApi)
        .importForBroker(
            eq("IBKR"), eq("statement.csv"), any(), eq("TELEGRAM"), eq("telegram-file-123"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void importByBroker_unknownBrokerReturns400() throws Exception {
    MockMultipartFile multipart =
        new MockMultipartFile(
            "file", "file.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

    mockMvc
        .perform(multipart("/import/broker/etoro").file(multipart).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void importByBroker_parserFailureReturns422() throws Exception {
    when(importApi.importForBroker(anyString(), anyString(), any(), anyString(), any()))
        .thenThrow(
            new InvestmentImportApi.ImportFailure(
                "Failed to import (batchId=5): boom", new IllegalStateException("boom")));

    MockMultipartFile multipart =
        new MockMultipartFile(
            "file", "file.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

    mockMvc
        .perform(multipart("/import/broker/XTB").file(multipart).with(csrf()))
        .andExpect(status().is(422));
  }

  @Test
  void importByBroker_requiresAuthentication() throws Exception {
    MockMultipartFile multipart =
        new MockMultipartFile(
            "file", "file.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, "payload".getBytes());

    mockMvc
        .perform(multipart("/import/broker/XTB").file(multipart).with(csrf()))
        .andExpect(status().isUnauthorized());
  }
}
