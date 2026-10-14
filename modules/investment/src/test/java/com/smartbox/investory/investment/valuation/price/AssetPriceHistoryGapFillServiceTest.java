package com.smartbox.investory.investment.valuation.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Asset Price History Gap Fill Service")
class AssetPriceHistoryGapFillServiceTest {

  @Mock private PositionRepository openedPositionRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;

  @DisplayName(
      "fill Missing Business Day Gaps backfills Business Days Between Known Rows And To As Of Date")
  @Test
  void fillMissingBusinessDayGaps_backfillsBusinessDaysBetweenKnownRowsAndToAsOfDate()
      throws Exception {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    PositionEntity opened = new PositionEntity();
    opened.setSymbol("IUVL");

    AssetEntity asset = new AssetEntity();
    asset.setId(101L);
    asset.setSymbol("IUVL");
    asset.setCurrency(CurrencyType.USD);

    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("IUVL"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPriceStartsBySymbolInBefore(
            Set.of("IUVL"), LocalDate.of(2026, 7, 17)))
        .thenReturn(List.of(start(LocalDate.of(2026, 7, 10))));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInRange(
            Set.of("IUVL"), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 17)))
        .thenReturn(
            List.of(
                row(
                    "IUVL",
                    LocalDate.of(2026, 7, 10),
                    18.50,
                    "USD",
                    80,
                    "STOOQ",
                    "VERIFIED_ALTERNATE_LISTING"),
                row(
                    "IUVL",
                    LocalDate.of(2026, 7, 17),
                    18.70,
                    "USD",
                    90,
                    "IBKR_TRADE",
                    "IBKR_TRADE_OBSERVATION")));

    service.fillMissingBusinessDayGaps(LocalDate.of(2026, 7, 17));

    ArgumentCaptor<String> batch = ArgumentCaptor.forClass(String.class);
    verify(assetPriceHistoryRepository, times(1)).upsertCarryForwardPrices(batch.capture());
    JsonNode rows = new ObjectMapper().readTree(batch.getValue());
    assertEquals(4, rows.size());
    assertEquals("2026-07-13", rows.get(0).get("price_date").asText());
    assertEquals("2026-07-16", rows.get(3).get("price_date").asText());
    assertEquals("2026-07-10", rows.get(0).get("source_date").asText());
    assertEquals("VERIFIED_ALTERNATE_LISTING", rows.get(0).get("scale_reason").asText());
    assertEquals("18.5", rows.get(0).get("price_value").asText());
  }

  @DisplayName("fill Missing Business Day Gaps does Not Use Carry Forward Row As Source")
  @Test
  void fillMissingBusinessDayGaps_doesNotUseCarryForwardRowAsSource() {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    PositionEntity opened = new PositionEntity();
    opened.setSymbol("IUVL");

    AssetEntity asset = new AssetEntity();
    asset.setId(101L);
    asset.setSymbol("IUVL");

    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("IUVL"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPriceStartsBySymbolInBefore(any(), any()))
        .thenReturn(List.of(start(LocalDate.of(2026, 7, 10))));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInRange(any(), any(), any()))
        .thenReturn(
            List.of(
                row(
                    "IUVL",
                    LocalDate.of(2026, 7, 10),
                    18.50,
                    "USD",
                    40,
                    "CARRY_FORWARD",
                    "STALE_CARRY_FORWARD")));

    service.fillMissingBusinessDayGaps(LocalDate.of(2026, 7, 17));

    verify(assetPriceHistoryRepository, never()).upsertCarryForwardPrices(any());
  }

  @DisplayName("fill Missing Business Day Gaps ignores Excluded Asset")
  @Test
  void fillMissingBusinessDayGaps_ignoresExcludedAsset() {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    PositionEntity opened = new PositionEntity();
    opened.setSymbol("EXCLUDED.US");
    AssetEntity asset = new AssetEntity();
    asset.setId(404L);
    asset.setSymbol("EXCLUDED.US");
    asset.setExcludeFromImport(true);

    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("EXCLUDED.US"))).thenReturn(List.of(asset));

    service.fillMissingBusinessDayGaps(LocalDate.of(2026, 7, 17));

    verify(assetPriceHistoryRepository, never())
        .findHistoricalPriceStartsBySymbolInBefore(any(), any());
    verify(assetPriceHistoryRepository, never())
        .findHistoricalPricesBySymbolInRange(any(), any(), any());
    verify(assetPriceHistoryRepository, never()).upsertCarryForwardPrices(any());
  }

  @Test
  void fillMissingBusinessDayGaps_usesBoundedWindowsAndPrecedingObservationAnchor()
      throws Exception {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);
    PositionEntity opened = new PositionEntity();
    opened.setSymbol("IUVL");
    AssetEntity asset = new AssetEntity();
    asset.setId(101L);
    asset.setSymbol("IUVL");

    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("IUVL"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPriceStartsBySymbolInBefore(any(), any()))
        .thenReturn(List.of(start(LocalDate.of(2026, 1, 1))));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInRange(any(), any(), any()))
        .thenReturn(
            List.of(
                row("IUVL", LocalDate.of(2026, 1, 1), 18.50, "USD", 80, "STOOQ", "VERIFIED"),
                row("IUVL", LocalDate.of(2026, 1, 30), 18.75, "USD", 90, "IBKR", "OBSERVED")),
            List.of(row("IUVL", LocalDate.of(2026, 1, 30), 18.75, "USD", 90, "IBKR", "OBSERVED")));

    service.fillMissingBusinessDayGaps(LocalDate.of(2026, 2, 15));

    ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
    verify(assetPriceHistoryRepository, times(2))
        .findHistoricalPricesBySymbolInRange(any(), from.capture(), to.capture());
    verify(assetPriceHistoryRepository)
        .findHistoricalPriceStartsBySymbolInBefore(any(), eq(LocalDate.of(2026, 2, 15)));
    assertEquals(List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)), from.getAllValues());
    assertEquals(List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 15)), to.getAllValues());

    ArgumentCaptor<String> batches = ArgumentCaptor.forClass(String.class);
    verify(assetPriceHistoryRepository, times(2)).upsertCarryForwardPrices(batches.capture());
    JsonNode secondWindow = new ObjectMapper().readTree(batches.getAllValues().get(1));
    assertEquals(10, secondWindow.size());
    assertEquals("2026-01-30", secondWindow.get(0).get("source_date").asText());
    assertEquals("18.75", secondWindow.get(0).get("price_value").asText());
  }

  @Test
  void fillMissingBusinessDayGaps_leavesEmptyHistoryUnchanged() {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);
    PositionEntity opened = new PositionEntity();
    opened.setSymbol("IUVL");
    AssetEntity asset = new AssetEntity();
    asset.setId(101L);
    asset.setSymbol("IUVL");
    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("IUVL"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPriceStartsBySymbolInBefore(any(), any()))
        .thenReturn(List.of());

    service.fillMissingBusinessDayGaps(LocalDate.of(2026, 2, 15));

    verify(assetPriceHistoryRepository, never())
        .findHistoricalPricesBySymbolInRange(any(), any(), any());
    verify(assetPriceHistoryRepository, never()).upsertCarryForwardPrices(any());
  }

  @Test
  void fillMissingBusinessDayGaps_keepsWeekdayHolidayBehaviorUnchanged() throws Exception {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);
    PositionEntity opened = new PositionEntity();
    opened.setSymbol("TSLA.US");
    AssetEntity asset = new AssetEntity();
    asset.setId(1001L);
    asset.setSymbol("TSLA.US");
    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("TSLA.US"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPriceStartsBySymbolInBefore(any(), any()))
        .thenReturn(List.of(start(LocalDate.of(2026, 7, 2))));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInRange(any(), any(), any()))
        .thenReturn(
            List.of(
                row("TSLA.US", LocalDate.of(2026, 7, 2), 300, "USD", 90, "STOOQ", "OBSERVED"),
                row("TSLA.US", LocalDate.of(2026, 7, 6), 305, "USD", 90, "STOOQ", "OBSERVED")));

    service.fillMissingBusinessDayGaps(LocalDate.of(2026, 7, 6));

    ArgumentCaptor<String> batch = ArgumentCaptor.forClass(String.class);
    verify(assetPriceHistoryRepository).upsertCarryForwardPrices(batch.capture());
    JsonNode rows = new ObjectMapper().readTree(batch.getValue());
    assertEquals(1, rows.size());
    assertEquals("2026-07-03", rows.get(0).get("price_date").asText());
  }

  @DisplayName("fill Missing Business Day Gaps prefers Open Lot Price When Split Observations Tie")
  @Test
  void fillMissingBusinessDayGaps_prefersOpenLotPriceWhenSplitObservationsTie() throws Exception {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    PositionEntity opened = new PositionEntity();
    opened.setSymbol("NFLX.US");
    AssetEntity asset = new AssetEntity();
    asset.setId(202L);
    asset.setSymbol("NFLX.US");

    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("NFLX.US"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPriceStartsBySymbolInBefore(any(), any()))
        .thenReturn(List.of(start(LocalDate.of(2025, 11, 16))));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInRange(any(), any(), any()))
        .thenReturn(
            List.of(
                row(
                    "NFLX.US",
                    LocalDate.of(2025, 11, 16),
                    1181.21,
                    "USD",
                    90,
                    "XTB_TRADE_CLOSE",
                    "XTB_TRADE_CLOSE_OBSERVATION"),
                row(
                    "NFLX.US",
                    LocalDate.of(2025, 11, 16),
                    112.98,
                    "USD",
                    90,
                    "XTB_TRADE_OPEN",
                    "XTB_TRADE_OPEN_OBSERVATION")));

    service.fillMissingBusinessDayGaps(LocalDate.of(2025, 11, 17));

    ArgumentCaptor<String> batch = ArgumentCaptor.forClass(String.class);
    verify(assetPriceHistoryRepository).upsertCarryForwardPrices(batch.capture());
    JsonNode rows = new ObjectMapper().readTree(batch.getValue());
    assertEquals(1, rows.size());
    assertEquals("112.98", rows.get(0).get("price_value").asText());
    assertEquals("XTB_TRADE_OPEN_OBSERVATION", rows.get(0).get("scale_reason").asText());
  }

  @DisplayName("fill Missing Business Day Gaps copies Currency And Scale Metadata")
  @Test
  void fillMissingBusinessDayGaps_copiesCurrencyAndScaleMetadata() throws Exception {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    PositionEntity opened = new PositionEntity();
    opened.setSymbol("VWCE.DE");
    AssetEntity asset = new AssetEntity();
    asset.setId(303L);
    asset.setSymbol("VWCE.DE");

    when(openedPositionRepository.findOpen()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("VWCE.DE"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPriceStartsBySymbolInBefore(any(), any()))
        .thenReturn(List.of(start(LocalDate.of(2026, 7, 10))));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInRange(any(), any(), any()))
        .thenReturn(
            List.of(
                scaledRow(
                    "VWCE.DE",
                    LocalDate.of(2026, 7, 10),
                    139.8,
                    "EUR",
                    BigDecimal.valueOf(0.01),
                    60,
                    "XTB_TRADE_OPEN",
                    "XTB_TRADE_OPEN_OBSERVATION"),
                row(
                    "VWCE.DE",
                    LocalDate.of(2026, 7, 17),
                    141.0,
                    "EUR",
                    90,
                    "IBKR_TRADE",
                    "IBKR_TRADE_OBSERVATION")));

    service.fillMissingBusinessDayGaps(LocalDate.of(2026, 7, 17));

    ArgumentCaptor<String> batch = ArgumentCaptor.forClass(String.class);
    verify(assetPriceHistoryRepository).upsertCarryForwardPrices(batch.capture());
    JsonNode rows = new ObjectMapper().readTree(batch.getValue());
    assertEquals("0.01", rows.get(0).get("price_scale_factor").asText());
    assertEquals("EUR", rows.get(0).get("price_currency").asText());
  }

  private static AssetPriceHistoryRepository.HistoricalAssetPriceRow row(
      String symbol,
      LocalDate date,
      double closePrice,
      String currency,
      int qualityScore,
      String origin,
      String qualityClass) {
    return new AssetPriceHistoryRepository.HistoricalAssetPriceRow() {
      @Override
      public String getSymbol() {
        return symbol;
      }

      @Override
      public LocalDate getPriceDate() {
        return date;
      }

      @Override
      public LocalDate getSourceDate() {
        return date;
      }

      @Override
      public String getSource() {
        return origin;
      }

      @Override
      public String getSourceSymbol() {
        return symbol;
      }

      @Override
      public String getOriginalSourceSymbol() {
        return symbol;
      }

      @Override
      public BigDecimal getClosePrice() {
        return BigDecimal.valueOf(closePrice);
      }

      @Override
      public String getPriceCurrency() {
        return currency;
      }

      @Override
      public BigDecimal getPriceScaleFactor() {
        return BigDecimal.ONE;
      }

      @Override
      public Integer getQualityScore() {
        return qualityScore;
      }

      @Override
      public String getQualityClass() {
        return qualityClass;
      }

      @Override
      public String getPriceOrigin() {
        return origin;
      }

      @Override
      public Boolean getEstimated() {
        return false;
      }

      @Override
      public LocalDate getInterpolationLeftDate() {
        return null;
      }

      @Override
      public LocalDate getInterpolationRightDate() {
        return null;
      }
    };
  }

  private static AssetPriceHistoryRepository.HistoricalPriceStart start(LocalDate date) {
    return new AssetPriceHistoryRepository.HistoricalPriceStart() {
      @Override
      public String getSymbol() {
        return "TEST";
      }

      @Override
      public LocalDate getPriceDate() {
        return date;
      }
    };
  }

  private static AssetPriceHistoryRepository.HistoricalAssetPriceRow scaledRow(
      String symbol,
      LocalDate date,
      double closePrice,
      String currency,
      BigDecimal scaleFactor,
      int qualityScore,
      String origin,
      String qualityClass) {
    AssetPriceHistoryRepository.HistoricalAssetPriceRow base =
        row(symbol, date, closePrice, currency, qualityScore, origin, qualityClass);
    return new AssetPriceHistoryRepository.HistoricalAssetPriceRow() {
      @Override
      public String getSymbol() {
        return base.getSymbol();
      }

      @Override
      public LocalDate getPriceDate() {
        return base.getPriceDate();
      }

      @Override
      public LocalDate getSourceDate() {
        return base.getSourceDate();
      }

      @Override
      public String getSource() {
        return base.getSource();
      }

      @Override
      public String getSourceSymbol() {
        return base.getSourceSymbol();
      }

      @Override
      public String getOriginalSourceSymbol() {
        return base.getOriginalSourceSymbol();
      }

      @Override
      public BigDecimal getClosePrice() {
        return base.getClosePrice();
      }

      @Override
      public String getPriceCurrency() {
        return base.getPriceCurrency();
      }

      @Override
      public BigDecimal getPriceScaleFactor() {
        return scaleFactor;
      }

      @Override
      public Integer getQualityScore() {
        return base.getQualityScore();
      }

      @Override
      public String getQualityClass() {
        return base.getQualityClass();
      }

      @Override
      public String getPriceOrigin() {
        return base.getPriceOrigin();
      }

      @Override
      public Boolean getEstimated() {
        return base.getEstimated();
      }

      @Override
      public LocalDate getInterpolationLeftDate() {
        return base.getInterpolationLeftDate();
      }

      @Override
      public LocalDate getInterpolationRightDate() {
        return base.getInterpolationRightDate();
      }
    };
  }
}
