package com.smartbox.investory.investment.reporting.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceChartRepository;
import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Asset Price Chart Service")
class AssetPriceChartServiceTest {

  private static final ClockApplicationTime TIME =
      new ClockApplicationTime(
          Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC),
          ZoneId.of("Europe/Warsaw"));

  private final AssetRepository assets = mock();
  private final AssetPriceChartRepository prices = mock();
  private final AssetPriceChartService service = new AssetPriceChartService(assets, prices, TIME);

  @DisplayName("normalizes Symbol And Maps Canonical Price Rows")
  @Test
  void normalizesSymbolAndMapsCanonicalPriceRows() {
    AssetEntity asset = AssetEntity.builder().id(4L).symbol("VWCE").build();
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

    var result = service.findBySymbol(1L, " vwce ", DashboardPeriod.MAX);

    assertThat(result)
        .singleElement()
        .satisfies(
            point -> {
              assertThat(point.closePrice())
                  .isEqualByComparingTo(java.math.BigDecimal.valueOf(123.45));
              assertThat(point.currency()).isEqualTo("EUR");
              assertThat(point.qualityClass()).isEqualTo("A");
            });
  }

  @DisplayName("rejects Blank Or Unknown Symbols Before Price Lookup")
  @Test
  void rejectsBlankOrUnknownSymbolsBeforePriceLookup() {
    assertThatThrownBy(() -> service.findBySymbol(1L, " ", DashboardPeriod.ONE_YEAR))
        .isInstanceOf(AssetDetailNotFoundException.class);
    when(assets.findBySymbol("NOPE")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findBySymbol(1L, "nope", DashboardPeriod.ONE_YEAR))
        .isInstanceOf(AssetDetailNotFoundException.class);
  }
}
