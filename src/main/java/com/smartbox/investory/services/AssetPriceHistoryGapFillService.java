package com.smartbox.investory.services;

import com.smartbox.investory.infrastructure.repository.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetPriceHistoryGapFillService {

  private final OpenedPositionRepository openedPositionRepository;
  private final AssetRepository assetRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;

  @Transactional
  public void fillMissingBusinessDayGaps(LocalDate asOfDate) {
    Set<String> openSymbols =
        openedPositionRepository.findAll().stream()
            .map(OpenedPosition::getSymbol)
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    if (openSymbols.isEmpty()) {
      return;
    }

    Map<String, Asset> assetsBySymbol =
        assetRepository.findAllBySymbolIn(openSymbols).stream()
            .filter(asset -> !Boolean.TRUE.equals(asset.getExcludeFromImport()))
            .collect(
                java.util.stream.Collectors.toMap(
                    Asset::getSymbol, asset -> asset, (existing, ignored) -> existing));
    if (assetsBySymbol.isEmpty()) {
      return;
    }

    List<AssetPriceHistoryRepository.HistoricalAssetPriceRow> rows =
        assetPriceHistoryRepository.findHistoricalPricesBySymbolInBefore(openSymbols, asOfDate);
    Map<String, NavigableMap<LocalDate, AssetPriceHistoryRepository.HistoricalAssetPriceRow>>
        bySymbol = new HashMap<>();
    for (AssetPriceHistoryRepository.HistoricalAssetPriceRow row : rows) {
      if (!StringUtils.hasText(row.getSymbol())
          || row.getPriceDate() == null
          || row.getClosePrice() == null
          || row.getClosePrice().compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      NavigableMap<LocalDate, AssetPriceHistoryRepository.HistoricalAssetPriceRow> datedRows =
          bySymbol.computeIfAbsent(row.getSymbol(), ignored -> new TreeMap<>());
      AssetPriceHistoryRepository.HistoricalAssetPriceRow existing =
          datedRows.get(row.getPriceDate());
      if (existing == null || compareRows(row, existing) < 0) {
        datedRows.put(row.getPriceDate(), row);
      }
    }

    int inserted = 0;
    for (Map.Entry<String, Asset> entry : assetsBySymbol.entrySet()) {
      String symbol = entry.getKey();
      Asset asset = entry.getValue();
      NavigableMap<LocalDate, AssetPriceHistoryRepository.HistoricalAssetPriceRow> datedRows =
          bySymbol.get(symbol);
      if (datedRows == null || datedRows.isEmpty()) {
        continue;
      }
      List<LocalDate> dates = List.copyOf(datedRows.navigableKeySet());
      for (int i = 0; i < dates.size(); i++) {
        LocalDate left = dates.get(i);
        LocalDate right = i + 1 < dates.size() ? dates.get(i + 1) : asOfDate.plusDays(1);
        AssetPriceHistoryRepository.HistoricalAssetPriceRow source = datedRows.get(left);
        if (source == null || shouldSkipCarryForwardSource(source)) {
          continue;
        }
        LocalDate day = left.plusDays(1);
        while (day.isBefore(right) && !day.isAfter(asOfDate)) {
          if (isBusinessDay(day)) {
            assetPriceHistoryRepository.upsertCarryForwardPrice(
                asset.getId(),
                day,
                left,
                source.getSymbol(),
                source.getSymbol(),
                source.getPriceCurrency(),
                source.getClosePrice(),
                source.getPriceScaleFactor() == null
                    ? BigDecimal.ONE
                    : source.getPriceScaleFactor(),
                source.getQualityClass());
            inserted++;
          }
          day = day.plusDays(1);
        }
      }
    }

    if (inserted > 0) {
      log.info(
          "Filled {} missing business-day asset_price_history gaps for open symbols", inserted);
    }
  }

  private static int compareRows(
      AssetPriceHistoryRepository.HistoricalAssetPriceRow left,
      AssetPriceHistoryRepository.HistoricalAssetPriceRow right) {
    return Comparator.comparingInt(AssetPriceHistoryGapFillService::priorityRank)
        .thenComparing(
            row -> row.getQualityScore() == null ? 0 : row.getQualityScore(),
            Comparator.reverseOrder())
        .thenComparingInt(AssetPriceHistoryGapFillService::tradeObservationTieRank)
        .compare(left, right);
  }

  private static int tradeObservationTieRank(
      AssetPriceHistoryRepository.HistoricalAssetPriceRow row) {
    String origin = upper(row.getPriceOrigin());
    if (origin.equals("XTB_TRADE_OPEN")) {
      return 0;
    }
    if (origin.equals("XTB_TRADE_CLOSE")) {
      return 2;
    }
    return 1;
  }

  private static int priorityRank(AssetPriceHistoryRepository.HistoricalAssetPriceRow row) {
    String quality = upper(row.getQualityClass());
    String origin = upper(row.getPriceOrigin());
    if (quality.contains("EXACT_LISTING_MARKET_CLOSE")) {
      return 1;
    }
    if (quality.contains("EXACT_LISTING_SCALED")) {
      return 2;
    }
    if (quality.contains("ALTERNATE") || quality.contains("PROXY")) {
      return 3;
    }
    if (quality.contains("INTERPOLATED")) {
      return 4;
    }
    if (quality.contains("TRADE_OBSERVATION") || origin.contains("TRADE")) {
      return 5;
    }
    return 6;
  }

  private static boolean shouldSkipCarryForwardSource(
      AssetPriceHistoryRepository.HistoricalAssetPriceRow row) {
    String quality = upper(row.getQualityClass());
    return quality.contains("STALE_CARRY_FORWARD");
  }

  private static boolean isBusinessDay(LocalDate date) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
  }

  private static String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
