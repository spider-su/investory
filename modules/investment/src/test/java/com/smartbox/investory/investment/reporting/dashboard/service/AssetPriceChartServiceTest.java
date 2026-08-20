package com.smartbox.investory.investment.reporting.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.Asset;
import com.smartbox.investory.investment.infrastructure.persistence.AssetPriceChartRepository;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssetPriceChartServiceTest {

  private final AssetRepository assets = mock();
  private final AssetPriceChartRepository prices = mock();
  private final AssetPriceChartService service = new AssetPriceChartService(assets, prices);

  @Test
  void normalizesSymbolAndMapsCanonicalPriceRows() {
    Asset asset = Asset.builder().id(4L).symbol("VWCE").build();
    var row = mock(AssetPriceChartRepository.AssetPriceChartRow.class);
    when(row.getPriceDate()).thenReturn(java.time.LocalDate.of(2026, 8, 1));
    when(row.getClosePrice()).thenReturn(new BigDecimal("123.45"));
    when(row.getPriceCurrency()).thenReturn("EUR");
    when(row.getSource()).thenReturn("IBKR");
    when(row.getQualityScore()).thenReturn(90);
    when(row.getQualityClass()).thenReturn("A");
    when(row.getPriceOrigin()).thenReturn("TRADE");
    when(assets.findBySymbol("VWCE")).thenReturn(Optional.of(asset));
    when(prices.findBestPrices(eq(4L), any(), any())).thenReturn(List.of(row));

    var result = service.findBySymbol(" vwce ", DashboardPeriod.MAX);

    assertThat(result)
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.closePrice()).isEqualTo(123.45);
              assertThat(point.currency()).isEqualTo("EUR");
              assertThat(point.qualityClass()).isEqualTo("A");
            });
  }

  @Test
  void rejectsBlankOrUnknownSymbolsBeforePriceLookup() {
    assertThatThrownBy(() -> service.findBySymbol(" ", DashboardPeriod.ONE_YEAR))
        .isInstanceOf(AssetDetailNotFoundException.class);
    when(assets.findBySymbol("NOPE")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findBySymbol("nope", DashboardPeriod.ONE_YEAR))
        .isInstanceOf(AssetDetailNotFoundException.class);
  }
}
