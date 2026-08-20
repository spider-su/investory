package com.smartbox.investory.investment.market.price;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.Asset;
import com.smartbox.investory.investment.infrastructure.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPositionRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
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
            BigDecimal.valueOf(18.50),
            BigDecimal.ONE,
            "VERIFIED_ALTERNATE_LISTING");
    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            101L,
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 10),
            "IUVL",
            "IUVL",
            "USD",
            BigDecimal.valueOf(18.50),
            BigDecimal.ONE,
            "VERIFIED_ALTERNATE_LISTING");
    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            101L,
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 10),
            "IUVL",
            "IUVL",
            "USD",
            BigDecimal.valueOf(18.50),
            BigDecimal.ONE,
            "VERIFIED_ALTERNATE_LISTING");
    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            101L,
            LocalDate.of(2026, 7, 16),
            LocalDate.of(2026, 7, 10),
            "IUVL",
            "IUVL",
            "USD",
            BigDecimal.valueOf(18.50),
            BigDecimal.ONE,
            "VERIFIED_ALTERNATE_LISTING");
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
        .upsertCarryForwardPrice(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void fillMissingBusinessDayGaps_ignoresExcludedAsset() {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    OpenedPosition opened = new OpenedPosition();
    opened.setSymbol("EXCLUDED.US");
    Asset asset = new Asset();
    asset.setId(404L);
    asset.setSymbol("EXCLUDED.US");
    asset.setExcludeFromImport(true);

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("EXCLUDED.US"))).thenReturn(List.of(asset));

    service.fillMissingBusinessDayGaps(LocalDate.of(2026, 7, 17));

    verify(assetPriceHistoryRepository, never()).findHistoricalPricesBySymbolInBefore(any(), any());
    verify(assetPriceHistoryRepository, never())
        .upsertCarryForwardPrice(any(), any(), any(), any(), any(), any(), any(), any(), any());
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
            BigDecimal.valueOf(112.98),
            BigDecimal.ONE,
            "XTB_TRADE_OPEN_OBSERVATION");
  }

  @Test
  void fillMissingBusinessDayGaps_copiesCurrencyAndScaleMetadata() {
    AssetPriceHistoryGapFillService service =
        new AssetPriceHistoryGapFillService(
            openedPositionRepository, assetRepository, assetPriceHistoryRepository);

    OpenedPosition opened = new OpenedPosition();
    opened.setSymbol("VWCE.DE");
    Asset asset = new Asset();
    asset.setId(303L);
    asset.setSymbol("VWCE.DE");

    when(openedPositionRepository.findAll()).thenReturn(List.of(opened));
    when(assetRepository.findAllBySymbolIn(Set.of("VWCE.DE"))).thenReturn(List.of(asset));
    when(assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(any(), any()))
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

    verify(assetPriceHistoryRepository)
        .upsertCarryForwardPrice(
            303L,
            LocalDate.of(2026, 7, 13),
            LocalDate.of(2026, 7, 10),
            "VWCE.DE",
            "VWCE.DE",
            "EUR",
            BigDecimal.valueOf(139.8),
            BigDecimal.valueOf(0.01),
            "XTB_TRADE_OPEN_OBSERVATION");
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
