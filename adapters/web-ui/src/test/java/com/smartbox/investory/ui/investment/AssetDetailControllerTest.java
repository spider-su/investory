package com.smartbox.investory.ui.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorMarketDataFacts;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

@DisplayName("Investment Asset Detail Controller")
class AssetDetailControllerTest {

  @DisplayName("passes The Complete Canonical Read Model To The Template")
  @Test
  void passesTheCompleteCanonicalReadModelToTheTemplate() {
    InvestmentAssetClient assets = mock(InvestmentAssetClient.class);
    AssetDetailView detail = canonicalTeslaDetail();
    List<AssetPricePointView> priceHistory =
        List.of(
            new AssetPricePointView(
                LocalDate.of(2025, 1, 1),
                HappyInvestorMarketDataFacts.TESLA_CLOSE,
                "USD",
                "STOOQ",
                95,
                "EXACT_LISTING_MARKET_CLOSE",
                "STOOQ_MARKET_CLOSE"));
    List<DashboardPeriod> periods = List.of(DashboardPeriod.YEAR_TO_DATE, DashboardPeriod.MAX);
    when(assets.detail(1L, "TSLA.US", DashboardPeriod.MAX)).thenReturn(detail);
    when(assets.priceHistory(1L, "TSLA.US", DashboardPeriod.MAX)).thenReturn(priceHistory);
    when(assets.periods()).thenReturn(periods);
    var model = new ConcurrentModel();

    String template =
        new AssetDetailController(assets).detail("TSLA.US", DashboardPeriod.MAX, 1L, model);

    assertThat(template).isEqualTo("dashboard/asset-detail");
    assertThat(model.getAttribute("asset")).isSameAs(detail);
    assertThat(model.getAttribute("priceHistory")).isSameAs(priceHistory);
    assertThat(model.getAttribute("periods")).isSameAs(periods);
    assertThat(model.getAttribute("portfolioId")).isEqualTo(1L);
    assertThat(detail.marketPrice())
        .isEqualTo(HappyInvestorMarketDataFacts.TESLA_CLOSE.doubleValue());
    verify(assets).detail(1L, "TSLA.US", DashboardPeriod.MAX);
    verify(assets).priceHistory(1L, "TSLA.US", DashboardPeriod.MAX);
    verify(assets).periods();
  }

  private static AssetDetailView canonicalTeslaDetail() {
    double price = HappyInvestorMarketDataFacts.TESLA_CLOSE.doubleValue();
    return new AssetDetailView(
        1001L,
        "TSLA.US",
        "Tesla, Inc.",
        "TSLA",
        "TSLA",
        "EQUITY",
        "US",
        CurrencyType.USD,
        price,
        price,
        "STOOQ",
        null,
        List.of(),
        1.0,
        price,
        price - 200.0,
        List.of(),
        0.0,
        List.of(),
        0.0,
        0.0,
        0.0,
        DashboardPeriod.MAX,
        null);
  }
}
