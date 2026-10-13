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
  void retainsFallbackAndFromAllocationCompatibility() {
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(50);
    var fallback = new PortfolioStructureQuery(null).load(7L, portfolio);
    assertThat(fallback.assetAllocation().totalValue()).isEqualByComparingTo("0");
    assertThat(fallback.topHoldings()).isEmpty();

    var compatible = new PortfolioStructureQuery(null).fromAllocation(portfolio, null);
    assertThat(compatible.assetAllocation().totalValue()).isEqualByComparingTo("50");
    assertThat(compatible.topHoldings()).isEmpty();
  }

  @Test
  void emptyRowsAndNonpositiveDenominatorProduceNoHoldingsAndZeroWeight() {
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(7L)).thenReturn(List.of());
    when(assets.findAllById(List.of())).thenReturn(List.of());
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(0);
    portfolio.setCash(-1);

    var view =
        new PortfolioStructureQuery(new AssetAllocationQuery(allocations, assets))
            .load(7L, portfolio);

    assertThat(view.assetAllocation().buckets()).isEmpty();
    assertThat(view.topHoldings()).isEmpty();
    assertThat(view.topFiveWeightPct()).isZero();
    assertThat(view.topTenWeightPct()).isZero();
  }

  @Test
  void sortsHoldingsDescendingAndTruncatesDisplayedListAndConcentrations() {
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    List<PortfolioAssetAllocationEntity> rows =
        java.util.stream.IntStream.rangeClosed(1, 12)
            .mapToObj(i -> row((long) i, "S" + i, 13 - i, i))
            .toList();
    when(allocations.findAllByPortfolioId(7L)).thenReturn(rows);
    when(assets.findAllById(java.util.stream.LongStream.rangeClosed(1, 12).boxed().toList()))
        .thenReturn(List.of());
    Portfolio portfolio = new Portfolio();
    portfolio.setCash(0);

    var view =
        new PortfolioStructureQuery(new AssetAllocationQuery(allocations, assets))
            .load(7L, portfolio);

    assertThat(view.topHoldings()).hasSize(10);
    assertThat(view.topHoldings())
        .extracting("symbol")
        .containsExactly("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10");
    assertThat(view.topHoldings())
        .extracting("value")
        .containsExactly(12.0, 11.0, 10.0, 9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0);
    assertThat(view.topFiveWeightPct())
        .isCloseTo(64.1025641026, org.assertj.core.data.Offset.offset(0.000001));
    assertThat(view.topTenWeightPct())
        .isCloseTo(96.1538461538, org.assertj.core.data.Offset.offset(0.000001));
  }

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

  @Test
  void canonicalHoldingsPreserveThresholdAndNullMonetaryFields() {
    PortfolioAssetAllocationEntity below = row(1L, "BELOW", 0.004, 111);
    PortfolioAssetAllocationEntity edge = row(2L, "EDGE", 0.005, 0);
    edge.setUnrealizedPlInBaseCurrency(null);
    PortfolioAssetAllocationEntity missing = row(3L, "NULL_VALUE", 1, 999);
    missing.setTotalValueInBaseCurrency(null);
    PortfolioAssetAllocationEntity first = row(4L, "AGG", 2, 0);
    first.setUnrealizedPlInBaseCurrency(null);
    PortfolioAssetAllocationEntity second = row(5L, "AGG", 3, 0.7);
    PortfolioAssetAllocationEntity noSymbol = row(6L, null, 1, 0);
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(7L))
        .thenReturn(List.of(below, edge, missing, first, second, noSymbol));
    when(assets.findAllById(List.of(1L, 2L, 3L, 4L, 5L, 6L))).thenReturn(List.of());
    Portfolio portfolio = new Portfolio();
    portfolio.setCash(1);

    var view =
        new PortfolioStructureQuery(new AssetAllocationQuery(allocations, assets))
            .load(7L, portfolio);

    assertThat(view.topHoldings()).extracting("symbol").containsExactly("AGG", "EDGE");
    assertThat(view.topHoldings()).extracting("value").containsExactly(5.0, 0.005);
    assertThat(view.topHoldings()).extracting("unrealized").containsExactly(0.7, 0.0);
    // The null-symbol valuation remains in allocation but never becomes a canonical holding.
    assertThat(view.assetAllocation().totalValue()).isEqualByComparingTo("7.005");
    assertThat(missing.getTotalValueInBaseCurrency()).isNull();
    assertThat(first.getUnrealizedPlInBaseCurrency()).isNull();
  }

  @Test
  void negativeValuationDenominatorKeepsSignedHoldingsWithZeroWeights() {
    PortfolioAssetAllocationRepository allocations = mock(PortfolioAssetAllocationRepository.class);
    AssetRepository assets = mock(AssetRepository.class);
    when(allocations.findAllByPortfolioId(7L))
        .thenReturn(List.of(row(1L, "NEG", -1, -0.2), row(2L, "EDGE", 0.005, 0)));
    when(assets.findAllById(List.of(1L, 2L))).thenReturn(List.of());
    Portfolio portfolio = new Portfolio();
    portfolio.setCash(-0.1);

    var view =
        new PortfolioStructureQuery(new AssetAllocationQuery(allocations, assets))
            .load(7L, portfolio);

    assertThat(view.assetAllocation().totalValue()).isEqualByComparingTo("-0.995");
    assertThat(view.topHoldings()).extracting("symbol").containsExactly("EDGE", "NEG");
    assertThat(view.topHoldings()).extracting("value").containsExactly(0.005, -1.0);
    assertThat(view.topHoldings()).extracting("unrealized").containsExactly(0.0, -0.2);
    assertThat(view.topHoldings()).extracting("weightPct").containsExactly(0.0, 0.0);
    assertThat(view.cashWeightPct()).isZero();
    assertThat(view.topFiveWeightPct()).isZero();
    assertThat(view.topTenWeightPct()).isZero();
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
