package com.smartbox.investory.investment.reporting.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.model.OpenPositionValue;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.performance.model.Portfolio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioStructureQueryTest {
  @Test
  void sharesOneLoadedSnapshotBetweenAllocationAndCanonicalHoldings() {
    PortfolioAssetAllocationEntity first = row(1L, "DUP", 120, 12);
    PortfolioAssetAllocationEntity second = row(1L, "DUP", 80, -2);
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(7L)).thenReturn(List.of(first, second));
    AssetEntity metadata = new AssetEntity();
    metadata.setId(1L);
    metadata.setAssetType("ETF");
    when(assets.findAllById(List.of(1L, 1L))).thenReturn(List.of(metadata));
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(9999);
    portfolio.setCash(100);

    var view =
        new PortfolioStructureQuery(new AssetAllocationQuery(allocations, assets))
            .load(7L, portfolio);

    assertThat(view.assetAllocation().totalValue()).isEqualByComparingTo("300");
    assertThat(view.assetAllocation().buckets()).extracting("name").containsExactly("ETF", "Cash");
    assertThat(view.assetAllocation().buckets().getFirst().value()).isEqualByComparingTo("200");
    assertThat(view.topHoldings()).hasSize(1);
    assertThat(view.topHoldings().getFirst().symbol()).isEqualTo("DUP");
    assertThat(view.topHoldings().getFirst().value()).isEqualTo(200);
    assertThat(view.topHoldings().getFirst().unrealized()).isEqualTo(10);
    verify(allocations).findAllByPortfolioId(7L);
    verifyNoMoreInteractions(allocations);
    verify(assets).findAllById(List.of(1L, 1L));
    verifyNoMoreInteractions(assets);
  }

  @Test
  void derivesConcentrationFromCanonicalValuedHoldings() {
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(7L))
        .thenReturn(List.of(row(1L, "AAA", 200, 20), row(2L, "BBB", 100, -5)));
    when(assets.findAllById(List.of(1L, 2L))).thenReturn(List.of());
    Portfolio portfolio = new Portfolio();
    // The reporting allocation total is the valuation basis; the aggregate Portfolio balance is
    // deliberately different so this test catches denominator drift.
    portfolio.setBalance(999);
    portfolio.setCash(0);
    portfolio.setOpenPositionValues(
        List.of(
            new OpenPositionValue(
                "LEGACY",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                com.smartbox.investory.shared.currency.CurrencyType.USD,
                BigDecimal.valueOf(9999),
                BigDecimal.valueOf(9999),
                null,
                com.smartbox.investory.shared.currency.CurrencyType.USD,
                BigDecimal.ZERO,
                null)));

    var view =
        new PortfolioStructureQuery(new AssetAllocationQuery(allocations, assets))
            .load(7L, portfolio);

    assertThat(view.topHoldings()).extracting("symbol").containsExactly("AAA", "BBB");
    assertThat(view.topFiveWeightPct())
        .isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.000001));
    assertThat(view.topHoldings().getFirst().unrealized()).isEqualTo(20);
  }

  private static PortfolioAssetAllocationEntity row(
      Long assetId, String symbol, double value, double unrealized) {
    PortfolioAssetAllocationEntity row = new PortfolioAssetAllocationEntity();
    row.setPortfolioId(7L);
    row.setAssetId(assetId);
    row.setAssetSymbol(symbol);
    row.setTotalValueInBaseCurrency(BigDecimal.valueOf(value));
    row.setUnrealizedPlInBaseCurrency(BigDecimal.valueOf(unrealized));
    return row;
  }
}
