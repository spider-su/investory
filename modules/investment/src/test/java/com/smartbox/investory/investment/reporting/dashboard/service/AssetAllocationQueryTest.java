package com.smartbox.investory.investment.reporting.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.accounting.model.Portfolio;
import com.smartbox.investory.investment.infrastructure.persistence.AssetEntity;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.reporting.dashboard.application.AssetAllocationView;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetAllocationQueryTest {
  @Test
  void aggregatesExistingValuationsByAssetTypeAndCash() {
    PortfolioAssetAllocationEntity position = new PortfolioAssetAllocationEntity();
    position.setAssetId(7L);
    position.setAssetSymbol("VWRA");
    position.setTotalValueInBaseCurrency(700d);
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

    assertThat(view.totalValue()).isEqualTo(1000);
    assertThat(view.buckets()).extracting("name").containsExactly("Equity / ETF", "Cash");
    assertThat(view.buckets().getFirst().weightPct()).isEqualTo(70.0);
    assertThat(view.buckets().stream().mapToDouble(AssetAllocationView.Bucket::value).sum())
        .isEqualTo(view.totalValue());
  }

  @Test
  void mapsKnownAssetTypesDeterministicallyAndUnknownTypesToOther() {
    List<PortfolioAssetAllocationEntity> rows =
        List.of(
            allocation(1L, "ETF", 10),
            allocation(2L, "REIT", 20),
            allocation(3L, "FIXED_INCOME", 30),
            allocation(4L, "METAL", 40),
            allocation(5L, "mystery", 50));
    List<AssetEntity> assets =
        List.of(
            asset(1L, "ETF"),
            asset(2L, "REIT"),
            asset(3L, "FIXED_INCOME"),
            asset(4L, "METAL"),
            asset(5L, "mystery"));
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assetRepository = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(1L)).thenReturn(rows);
    when(assetRepository.findAllById(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(assets);

    var view = new AssetAllocationQuery(allocations, assetRepository).load(1L, new Portfolio());

    assertThat(view.buckets())
        .extracting("name")
        .containsExactly(
            "Other", "Commodity / metal", "Fixed income", "REIT / real estate", "Equity / ETF");
  }

  @Test
  void loadsOnlyRowsForRequestedPortfolio() {
    PortfolioAssetAllocationEntity first = allocation(1L, "VWRA", 70);
    first.setPortfolioId(1L);
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assetRepository = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(1L)).thenReturn(List.of(first));
    when(assetRepository.findAllById(List.of(1L))).thenReturn(List.of(asset(1L, "ETF")));

    var view = new AssetAllocationQuery(allocations, assetRepository).load(1L, new Portfolio());

    assertThat(view.buckets()).extracting("name").containsExactly("Equity / ETF");
    assertThat(view.buckets().getFirst().value()).isEqualTo(70);
  }

  private static PortfolioAssetAllocationEntity allocation(Long id, String symbol, double value) {
    PortfolioAssetAllocationEntity allocation = new PortfolioAssetAllocationEntity();
    allocation.setPortfolioId(1L);
    allocation.setAssetId(id);
    allocation.setAssetSymbol(symbol);
    allocation.setTotalValueInBaseCurrency(value);
    return allocation;
  }

  private static AssetEntity asset(Long id, String type) {
    AssetEntity asset = new AssetEntity();
    asset.setId(id);
    asset.setAssetType(type);
    return asset;
  }
}
