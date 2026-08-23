package com.smartbox.investory.ui.investment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.MockMvcSecurityTestConfig;
import com.smartbox.investory.config.SecurityConfig;
import com.smartbox.investory.investment.api.InvestmentMaintenanceApi;
import com.smartbox.investory.investment.market.price.ManualAssetPriceService.ManualAssetPrice;
import com.smartbox.investory.shared.currency.CurrencyType;
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
  @MockitoBean private InvestmentMaintenanceApi maintenance;

  @Test
  @WithMockUser(roles = "ADMIN")
  void refreshPricesRunsFullPortfolioUpdate() throws Exception {
    when(maintenance.refreshPrices())
        .thenReturn(
            new InvestmentMaintenanceApi.MaintenanceResult(
                "OK", "Open position prices refreshed", ZonedDateTime.now()));
    mockMvc
        .perform(post("/admin/refresh-prices").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.message").value("Open position prices refreshed"));

    verify(maintenance).refreshPrices();
  }

  @Test
  void refreshPricesRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/admin/refresh-prices").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void refreshCurrencyRunsFxUpdate() throws Exception {
    when(maintenance.refreshCurrency())
        .thenReturn(java.util.Map.of("updated", java.util.List.of("USD", "EUR", "PLN")));

    mockMvc
        .perform(post("/admin/refresh-currency").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated[0]").value("USD"));

    verify(maintenance).refreshCurrency();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void rebuildMonthlyRunsProjectionRecalculation() throws Exception {
    when(maintenance.rebuildMonthly())
        .thenReturn(
            new InvestmentMaintenanceApi.MaintenanceResult(
                "OK", "AccountEntity stats rebuilt", ZonedDateTime.now()));
    mockMvc
        .perform(post("/admin/rebuild-monthly").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.message").value("AccountEntity stats rebuilt"));

    verify(maintenance).rebuildMonthly();
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
    when(maintenance.updateManualAssetPrice("SGLD.UK", 15.25))
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

    verify(maintenance).updateManualAssetPrice("SGLD.UK", 15.25);
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
    when(maintenance.updateManualAssetPrice("SGLD.UK", -1.0))
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
