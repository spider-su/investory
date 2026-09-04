package com.smartbox.investory.investment.reporting.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.model.AssetAllocationView;
import com.smartbox.investory.investment.performance.model.Portfolio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioStructureQueryTest {
  @Test
  void derivesConcentrationFromCanonicalValuedHoldings() {
    AssetAllocationQuery allocation = mock(AssetAllocationQuery.class);
    when(allocation.load(eq(7L), any(Portfolio.class)))
        .thenReturn(new AssetAllocationView(300, List.of()));
    when(allocation.canonicalHoldings(eq(7L)))
        .thenReturn(
            List.of(
                new AssetAllocationQuery.CanonicalHolding(
                    "AAA", BigDecimal.valueOf(200), BigDecimal.valueOf(20)),
                new AssetAllocationQuery.CanonicalHolding(
                    "BBB", BigDecimal.valueOf(100), BigDecimal.valueOf(-5))));
    Portfolio portfolio = new Portfolio();
    // The reporting allocation total is the valuation basis; the aggregate Portfolio balance is
    // deliberately different so this test catches denominator drift.
    portfolio.setBalance(999);
    portfolio.setCash(0);
    portfolio.setOpenPositionValues(List.of());

    var view = new PortfolioStructureQuery(allocation).load(7L, portfolio);

    assertThat(view.topHoldings()).extracting("symbol").containsExactly("AAA", "BBB");
    assertThat(view.topFiveWeightPct())
        .isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.000001));
    assertThat(view.topHoldings().getFirst().unrealized()).isEqualTo(20);
  }
}
