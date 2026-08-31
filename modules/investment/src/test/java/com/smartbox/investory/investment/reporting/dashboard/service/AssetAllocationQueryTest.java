package com.smartbox.investory.investment.reporting.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.model.AssetAllocationView;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.performance.model.Portfolio;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Asset Allocation Query")
class AssetAllocationQueryTest {
  @DisplayName("aggregates Existing Valuations By Asset Type And Cash")
  @Test
  void aggregatesExistingValuationsByAssetTypeAndCash() {
    PortfolioAssetAllocationEntity position = new PortfolioAssetAllocationEntity();
    position.setAssetId(7L);
    position.setAssetSymbol("VWRA");
    position.setTotalValueInBaseCurrency(java.math.BigDecimal.valueOf(700));
    AssetEntity asset = new AssetEntity();
    asset.setId(7L);
    asset.setAssetType("ETF");

    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(1L)).thenReturn(List.of(position));
    when(assets.findAllById(List.of(7L))).thenReturn(List.of(asset));
    Portfolio portfolio = new Portfolio();
    portfolio.setCash(300);

    var view = new AssetAllocationQuery(allocations, assets).load(1L, portfolio);

    assertThat(view.totalValue()).isEqualByComparingTo(java.math.BigDecimal.valueOf(1000));
    assertThat(view.buckets()).extracting("name").containsExactly("ETF", "Cash");
    assertThat(view.buckets().getFirst().weightPct()).isEqualTo(java.math.BigDecimal.valueOf(70.0));
    assertThat(view.buckets())
        .extracting(AssetAllocationView.Bucket::cssKey)
        .containsExactly("etf", "cash");
    assertThat(
            view.buckets().stream()
                .map(AssetAllocationView.Bucket::value)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
        .isEqualTo(view.totalValue());
  }

  @DisplayName("maps Known Asset Types Deterministically And Unknown Types To Other")
  @Test
  void mapsKnownAssetTypesDeterministicallyAndUnknownTypesToOther() {
    List<PortfolioAssetAllocationEntity> rows =
        List.of(
            allocation(1L, "ETF", 10),
            allocation(2L, "REIT", 20),
            allocation(3L, "FIXED_INCOME", 30),
            allocation(4L, "METAL", 40),
            allocation(5L, "mystery", 50),
            allocation(6L, "STOCK", 60));
    List<AssetEntity> assets =
        List.of(
            asset(1L, "ETF"),
            asset(2L, "REIT"),
            asset(3L, "FIXED_INCOME"),
            asset(4L, "METAL"),
            asset(5L, "mystery"),
            asset(6L, "EQUITY"));
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assetRepository = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(1L)).thenReturn(rows);
    when(assetRepository.findAllById(List.of(1L, 2L, 3L, 4L, 5L, 6L))).thenReturn(assets);

    var view = new AssetAllocationQuery(allocations, assetRepository).load(1L, new Portfolio());

    assertThat(view.buckets())
        .extracting("name")
        .containsExactly(
            "Equity", "Other", "Commodity / metal", "Fixed income", "REIT / real estate", "ETF");
    assertThat(view.buckets())
        .extracting(AssetAllocationView.Bucket::cssKey)
        .containsExactly("equity", "other", "commodity", "fixed-income", "real-estate", "etf");
  }

  @DisplayName("loads Only Rows For Requested Portfolio")
  @Test
  void loadsOnlyRowsForRequestedPortfolio() {
    PortfolioAssetAllocationEntity first = allocation(1L, "VWRA", 70);
    first.setPortfolioId(1L);
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assetRepository = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(1L)).thenReturn(List.of(first));
    when(assetRepository.findAllById(List.of(1L))).thenReturn(List.of(asset(1L, "ETF")));

    var view = new AssetAllocationQuery(allocations, assetRepository).load(1L, new Portfolio());

    assertThat(view.buckets()).extracting("name").containsExactly("ETF");
    assertThat(view.buckets().getFirst().value())
        .isEqualByComparingTo(java.math.BigDecimal.valueOf(70));
  }

  private static PortfolioAssetAllocationEntity allocation(Long id, String symbol, double value) {
    PortfolioAssetAllocationEntity allocation = new PortfolioAssetAllocationEntity();
    allocation.setPortfolioId(1L);
    allocation.setAssetId(id);
    allocation.setAssetSymbol(symbol);
    allocation.setTotalValueInBaseCurrency(java.math.BigDecimal.valueOf(value));
    return allocation;
  }

  private static AssetEntity asset(Long id, String type) {
    AssetEntity asset = new AssetEntity();
    asset.setId(id);
    asset.setAssetType(type);
    return asset;
  }
}
