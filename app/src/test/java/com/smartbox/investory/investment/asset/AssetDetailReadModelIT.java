package com.smartbox.investory.investment.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.reporting.dashboard.application.InvestmentAssetApplicationService;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorMarketDataFacts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

/** Crosses persisted accounting, valuation and the asset REST read model. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AssetDetailReadModelIT extends FastDatabaseTest {
  @Autowired private MockMvc mvc;
  @Autowired private InvestmentAssetApplicationService assets;

  @Test
  void readsTheSameCanonicalAssetThroughServiceAndRestAndFailsClosedForMissingAsset()
      throws Exception {
    AssetDetailView view = assets.detail(1L, "TSLA.US", DashboardPeriod.MAX);
    assertThat(view.id()).isEqualTo(1001L);
    assertThat(view.symbol()).isEqualTo("TSLA.US");
    assertThat(view.name()).isEqualTo("Tesla, Inc.");
    assertThat(view.ticker()).isEqualTo("TSLA");
    assertThat(view.currency()).hasToString("USD");
    assertThat(view.marketPrice())
        .isEqualTo(HappyInvestorMarketDataFacts.TESLA_CLOSE.doubleValue());
    assertThat(view.marketPriceUsd())
        .isEqualTo(HappyInvestorMarketDataFacts.TESLA_CLOSE.doubleValue());
    assertThat(view.holdings()).hasSize(1);
    assertThat(view.holdings().getFirst().quantity()).isEqualTo(1.0);
    assertThat(view.holdings().getFirst().averageCost()).isEqualTo(200.0);
    assertThat(view.totalQuantity()).isEqualTo(1.0);
    assertThat(view.totalMarketValue())
        .isEqualTo(HappyInvestorMarketDataFacts.TESLA_CLOSE.doubleValue());
    assertThat(view.totalUnrealizedProfitLoss())
        .isEqualTo(HappyInvestorMarketDataFacts.TESLA_CLOSE.doubleValue() - 200.0);
    assertThat(view.totalRealizedProfitLoss()).isEqualTo(0.0);
    assertThat(view.transactions()).isNotNull();
    assertThat(view.transactions()).isEmpty();
    assertThat(view.dividends()).isEmpty();

    mvc.perform(
            get("/api/v1/investment/assets/TSLA.US")
                .param("portfolioId", "1")
                .param("period", "MAX")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/investment/assets/NOT-A-REAL-ASSET")
                .param("portfolioId", "1")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  void priceHistoryHonorsPeriodAndPortfolioBoundary() {
    var all = assets.priceHistory(1L, "TSLA.US", DashboardPeriod.MAX);
    var ytd = assets.priceHistory(1L, "TSLA.US", DashboardPeriod.YEAR_TO_DATE);
    assertThat(all).isNotEmpty();
    assertThat(all)
        .anySatisfy(
            point -> {
              assertThat(point.date()).isEqualTo(java.time.LocalDate.of(2025, 1, 1));
              assertThat(point.closePrice()).isEqualByComparingTo("403.840");
              assertThat(point.currency()).isEqualTo("USD");
              assertThat(point.source()).isEqualTo("STOOQ");
            });
    assertThat(ytd).allMatch(point -> !point.date().isBefore(java.time.LocalDate.of(2026, 1, 1)));
    assertThat(assets.detail(999999L, "VWRA.UK", DashboardPeriod.MAX).holdings()).isEmpty();
  }
}
