package com.example.demo.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetPriceHistoryGapFillServiceTest {

  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;

  @Test
  void fillMissingBusinessDayGaps_backfillsBusinessDaysBetweenKnownRowsAndToAsOfDate() {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    OpenedPosition opened = new OpenedPosition();
    opened.setSymbol("IUVL");

    Asset asset = new Asset();
    asset.setId(101L);
    asset.setSymbol("IUVL");
    asset.setCurrency(CurrencyType.USD);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("IUVL"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(
            Set.of("IUVL"), LocalDate.of(2026, 7, 17)))
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

    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            101L,
            LocalDate.of(2026, 7, 13),
            LocalDate.of(2026, 7, 10),
            "IUVL",
            "IUVL",
            "USD",
            BigDecimal.valueOf(18.50));
    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            101L,
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 10),
            "IUVL",
            "IUVL",
            "USD",
            BigDecimal.valueOf(18.50));
    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            101L,
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 10),
            "IUVL",
            "IUVL",
            "USD",
            BigDecimal.valueOf(18.50));
    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            101L,
            LocalDate.of(2026, 7, 16),
            LocalDate.of(2026, 7, 10),
            "IUVL",
            "IUVL",
            "USD",
            BigDecimal.valueOf(18.50));
  }

  @Test
  void fillMissingBusinessDayGaps_doesNotUseCarryForwardRowAsSource() {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    OpenedPosition opened = new OpenedPosition();
    opened.setSymbol("IUVL");

    Asset asset = new Asset();
    asset.setId(101L);
    asset.setSymbol("IUVL");

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("IUVL"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
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

    verify(assetPriceHistoryRepository, never())
        .upsertCarryForwardPrice(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void fillMissingBusinessDayGaps_prefersOpenLotPriceWhenSplitObservationsTie() {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    OpenedPosition opened = new OpenedPosition();
    opened.setSymbol("NFLX.US");
    Asset asset = new Asset();
    asset.setId(202L);
    asset.setSymbol("NFLX.US");

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("NFLX.US"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
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

    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            202L,
            LocalDate.of(2025, 11, 17),
            LocalDate.of(2025, 11, 16),
            "NFLX.US",
            "NFLX.US",
            "USD",
            BigDecimal.valueOf(112.98));
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
    };
  }
}
