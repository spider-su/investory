package com.smartbox.investory.ui.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DevToolsWellKnownControllerTest {
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new DevToolsWellKnownController()).build();
  }

  @Test
  void chromeDevToolsProbeReturnsEmptyJson() throws Exception {
    mockMvc
        .perform(get("/.well-known/appspecific/com.chrome.devtools.json"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(content().json("{}"));
  }
}
