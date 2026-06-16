package com.example.demo.controllers.rest;

import com.example.demo.config.SecurityConfig;
import com.example.demo.services.YahooExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExportController.class)
@Import(SecurityConfig.class)
class ExportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private YahooExportService exportService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void save_callsServiceAndReturnsPath() throws Exception {
        mockMvc.perform(post("/export/save"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("yahoo_export.csv")));

        verify(exportService).exportToYahooCsv("yahoo_export.csv");
    }

    @Test
    @WithMockUser(roles = "USER")
    void save_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/export/save"))
                .andExpect(status().isForbidden());
    }
}

