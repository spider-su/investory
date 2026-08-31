package com.smartbox.investory.ui.investment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.config.MockMvcSecurityTestConfig;
import com.smartbox.investory.config.SecurityConfig;
import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import com.smartbox.investory.investment.api.operations.ManualAssetPriceView;
import com.smartbox.investory.investment.web.DashboardRefreshController;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardRefreshController.class)
@Import({SecurityConfig.class, MockMvcSecurityTestConfig.class})
@DisplayName("Dashboard Refresh Controller")
class DashboardRefreshControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private InvestmentMaintenanceApi maintenance;

  @DisplayName("refresh Prices Runs Full Portfolio Update")
  @Test
  @WithMockUser(roles = "ADMIN")
  void refreshPricesRunsFullPortfolioUpdate() throws Exception {
    when(maintenance.refreshPrices())
        .thenReturn(
            new InvestmentMaintenanceApi.MaintenanceResult(
                "OK", "Open position prices refreshed", ZonedDateTime.now()));
    mockMvc
        .perform(post("/api/v1/investment/maintenance/refresh-prices").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.message").value("Open position prices refreshed"));

    verify(maintenance).refreshPrices();
  }

  @DisplayName("refresh Prices Requires Authentication")
  @Test
  void refreshPricesRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/investment/maintenance/refresh-prices").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @DisplayName("refresh Currency Runs Fx Update")
  @Test
  @WithMockUser(roles = "ADMIN")
  void refreshCurrencyRunsFxUpdate() throws Exception {
    when(maintenance.refreshCurrency())
        .thenReturn(
            new InvestmentMaintenanceApi.CurrencyRefreshResult(
                java.time.LocalDate.now(),
                java.util.List.of("USD", "EUR", "PLN"),
                java.util.List.of()));

    mockMvc
        .perform(post("/api/v1/investment/maintenance/refresh-currency").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated[0]").value("USD"));

    verify(maintenance).refreshCurrency();
  }

  @DisplayName("rebuild Monthly Runs Projection Recalculation")
  @Test
  @WithMockUser(roles = "ADMIN")
  void rebuildMonthlyRunsProjectionRecalculation() throws Exception {
    when(maintenance.rebuildMonthly())
        .thenReturn(
            new InvestmentMaintenanceApi.MaintenanceResult(
                "OK", "AccountEntity stats rebuilt", ZonedDateTime.now()));
    mockMvc
        .perform(post("/api/v1/investment/maintenance/rebuild-monthly").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.message").value("AccountEntity stats rebuilt"));

    verify(maintenance).rebuildMonthly();
  }

  @DisplayName("rebuild Monthly Requires Authentication")
  @Test
  void rebuildMonthlyRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/investment/maintenance/rebuild-monthly").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @DisplayName("update Manual Asset Price Returns Updated Price")
  @Test
  @WithMockUser(roles = "ADMIN")
  void updateManualAssetPriceReturnsUpdatedPrice() throws Exception {
    ZonedDateTime updatedAt = ZonedDateTime.parse("2026-07-16T09:00:00Z");
    when(maintenance.updateManualAssetPrice("SGLD.UK", BigDecimal.valueOf(15.25)))
        .thenReturn(
            new ManualAssetPriceView(
                "SGLD.UK",
                BigDecimal.valueOf(15.25),
                BigDecimal.valueOf(15.25),
                CurrencyType.USD,
                "Manual",
                updatedAt));

    mockMvc
        .perform(
            post("/api/v1/investment/maintenance/assets/SGLD.UK/price")
                .with(csrf())
                .contentType("application/json")
                .content("{\"marketPrice\":15.25}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.symbol").value("SGLD.UK"))
        .andExpect(jsonPath("$.marketPrice").value(15.25))
        .andExpect(jsonPath("$.source").value("Manual"));

    verify(maintenance).updateManualAssetPrice("SGLD.UK", BigDecimal.valueOf(15.25));
  }

  @DisplayName("update Manual Asset Price Requires Authentication")
  @Test
  void updateManualAssetPriceRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/investment/maintenance/assets/SGLD.UK/price")
                .with(csrf())
                .contentType("application/json")
                .content("{\"marketPrice\":15.25}"))
        .andExpect(status().isUnauthorized());
  }

  @DisplayName("update Manual Asset Price Requires Admin Role")
  @Test
  @WithMockUser(roles = "USER")
  void updateManualAssetPriceRequiresAdminRole() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/investment/maintenance/assets/SGLD.UK/price")
                .with(csrf())
                .contentType("application/json")
                .content("{\"marketPrice\":15.25}"))
        .andExpect(status().isForbidden());
  }

  @DisplayName("update Manual Asset Price Maps Invalid Input To Bad Request")
  @Test
  @WithMockUser(roles = "ADMIN")
  void updateManualAssetPriceMapsInvalidInputToBadRequest() throws Exception {
    when(maintenance.updateManualAssetPrice("SGLD.UK", new BigDecimal("-1")))
        .thenThrow(
            new InvestmentMaintenanceApi.InvalidMaintenanceRequest(
                "Market price must be positive", null));

    mockMvc
        .perform(
            post("/api/v1/investment/maintenance/assets/SGLD.UK/price")
                .with(csrf())
                .contentType("application/json")
                .content("{\"marketPrice\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Market price must be positive"));
  }
}
