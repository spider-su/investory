package com.example.demo.controllers.rest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.MockMvcSecurityTestConfig;
import com.example.demo.config.SecurityConfig;
import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.services.ManualAssetPriceService;
import com.example.demo.services.ManualAssetPriceService.ManualAssetPrice;
import com.example.demo.services.MarketService;
import com.example.demo.services.PortfolioProjectionService;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardRefreshController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
class DashboardRefreshControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private MarketService marketService;
  @MockitoBean private ManualAssetPriceService manualAssetPriceService;
  @MockitoBean private PortfolioProjectionService portfolioProjectionService;

  @Test
  @WithMockUser(roles = "ADMIN")
  void refreshPricesRunsFullPortfolioUpdate() throws Exception {
    mockMvc
        .perform(post("/admin/refresh-prices").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.message").value("Open position prices refreshed"));

    verify(marketService).fullPortfolioUpdate();
  }

  @Test
  void refreshPricesRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/admin/refresh-prices").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void rebuildMonthlyRunsProjectionRecalculation() throws Exception {
    mockMvc
        .perform(post("/admin/rebuild-monthly").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.message").value("Account stats rebuilt"));

    verify(portfolioProjectionService).recalculateAll();
  }

  @Test
  void rebuildMonthlyRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/admin/rebuild-monthly").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void updateManualAssetPriceReturnsUpdatedPrice() throws Exception {
    ZonedDateTime updatedAt = ZonedDateTime.parse("2026-07-16T09:00:00Z");
    when(manualAssetPriceService.updatePrice("SGLD.UK", 15.25))
        .thenReturn(
            new ManualAssetPrice("SGLD.UK", 15.25, 15.25, CurrencyType.USD, "Manual", updatedAt));

    mockMvc
        .perform(
            post("/admin/assets/SGLD.UK/price")
                .with(csrf())
                .contentType("application/json")
                .content("{\"marketPrice\":15.25}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.symbol").value("SGLD.UK"))
        .andExpect(jsonPath("$.marketPrice").value(15.25))
        .andExpect(jsonPath("$.source").value("Manual"));

    verify(manualAssetPriceService).updatePrice("SGLD.UK", 15.25);
  }

  @Test
  void updateManualAssetPriceRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/admin/assets/SGLD.UK/price")
                .with(csrf())
                .contentType("application/json")
                .content("{\"marketPrice\":15.25}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "USER")
  void updateManualAssetPriceRequiresAdminRole() throws Exception {
    mockMvc
        .perform(
            post("/admin/assets/SGLD.UK/price")
                .with(csrf())
                .contentType("application/json")
                .content("{\"marketPrice\":15.25}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void updateManualAssetPriceMapsInvalidInputToBadRequest() throws Exception {
    when(manualAssetPriceService.updatePrice("SGLD.UK", -1.0))
        .thenThrow(new IllegalArgumentException("Market price must be positive"));

    mockMvc
        .perform(
            post("/admin/assets/SGLD.UK/price")
                .with(csrf())
                .contentType("application/json")
                .content("{\"marketPrice\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Market price must be positive"));
  }
}
