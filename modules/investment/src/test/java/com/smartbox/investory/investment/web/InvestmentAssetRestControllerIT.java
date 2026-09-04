package com.smartbox.investory.investment.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("Investment Asset Rest Controller")
class InvestmentAssetRestControllerIT {
  private InvestmentAssetApi assets;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    assets = mock(InvestmentAssetApi.class);
    var conversionService = new DefaultFormattingConversionService();
    new InvestmentWebConversionConfig().addFormatters(conversionService);
    mvc =
        MockMvcBuilders.standaloneSetup(new InvestmentAssetRestController(assets))
            .setConversionService(conversionService)
            .build();
  }

  @DisplayName("asset Read Endpoints Bind Path And Optional Period")
  @Test
  void assetReadEndpointsBindPathAndOptionalPeriod() throws Exception {
    mvc.perform(get("/api/v1/investment/assets/periods")).andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/investment/assets/AAPL").param("period", "YTD").param("portfolioId", "7"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/investment/assets/AAPL/price-history")
                .param("period", "YTD")
                .param("portfolioId", "7"))
        .andExpect(status().isOk());
    verify(assets).periods();
    verify(assets).detail(7L, "AAPL", DashboardPeriod.YEAR_TO_DATE);
    verify(assets).priceHistory(7L, "AAPL", DashboardPeriod.YEAR_TO_DATE);
  }

  @Test
  void assetReadEndpointsRejectMissingOrInvalidPortfolio() throws Exception {
    mvc.perform(get("/api/v1/investment/assets/AAPL")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/investment/assets/AAPL").param("portfolioId", "0"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(assets);
  }
}
