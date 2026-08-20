package com.smartbox.investory.investment.market.price;

import com.smartbox.investory.investment.infrastructure.persistence.Asset;
import com.smartbox.investory.investment.infrastructure.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPositionRepository;
import com.smartbox.investory.investment.market.fx.currency.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetPriceFallbackService {

  private static final CurrencyType BASE_CURRENCY = CurrencyType.USD;

  private final OpenedPositionRepository openedPositionRepository;
  private final AssetRepository assetRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final CurrencyRateService currencyRateService;

  @Transactional
  public void populateMissingPricesFromOpenPositions() {
    Map<String, WeightedPrice> weightedPrices =
        openedPositionRepository.findAll().stream()
            .filter(position -> StringUtils.hasText(position.getSymbol()))
            .filter(position -> nz(position.getVolume()) > 0.0)
            .filter(position -> position.getOpenPrice() != null)
            .collect(
                Collectors.groupingBy(
                    OpenedPosition::getSymbol,
                    Collectors.collectingAndThen(Collectors.toList(), this::weightedOpenPrice)));

    weightedPrices.values().removeIf(Objects::isNull);
    if (weightedPrices.isEmpty()) {
      return;
    }

    ZonedDateTime now = ZonedDateTime.now();
    LocalDate rateDate = now.toLocalDate();
    Map<String, Asset> assetsBySymbol =
        assetRepository.findAllBySymbolIn(weightedPrices.keySet()).stream()
            .filter(asset -> !Boolean.TRUE.equals(asset.getExcludeFromImport()))
            .collect(
                Collectors.toMap(Asset::getSymbol, Function.identity(), (left, right) -> right));
    Map<String, HistoricalQuote> historicalQuotesBySymbol =
        assetPriceHistoryRepository
            .findHistoricalPricesBySymbolInBefore(weightedPrices.keySet(), rateDate)
            .stream()
            .filter(row -> row.getPriceDate() != null)
            .collect(
                Collectors.toMap(
                    AssetPriceHistoryRepository.HistoricalAssetPriceRow::getSymbol,
                    row ->
                        new HistoricalQuote(
                            row.getClosePrice(),
                            parseCurrency(row.getPriceCurrency()),
                            row.getPriceOrigin(),
                            row.getPriceDate(),
                            row.getQualityScore()),
                    AssetPriceFallbackService::preferHistoricalQuote));
    Collection<Asset> changed = new ArrayList<>();
    weightedPrices.forEach(
        (symbol, weightedPrice) -> {
          Asset asset = assetsBySymbol.get(symbol);
          if (asset == null) {
            log.warn("Skipping fallback price for {} because asset catalog row is missing", symbol);
            return;
          }
          CurrencyType currency =
              asset.getCurrency() != null ? asset.getCurrency() : weightedPrice.currency();
          if (currency == null) {
            currency = BASE_CURRENCY;
          }

          boolean fallbackSource =
              !StringUtils.hasText(asset.getPriceSource())
                  || "OpenPositionWeightedAverage".equals(asset.getPriceSource())
                  || "OPEN_PRICE_FALLBACK".equals(asset.getPriceSource());
          boolean updated = false;
          HistoricalQuote historicalQuote = historicalQuotesBySymbol.get(symbol);
          if (historicalQuote != null
              && historicalQuote.price() != null
              && historicalQuote.price().compareTo(BigDecimal.ZERO) > 0
              && (fallbackSource
                  || asset.getMarketPrice() == null
                  || asset.getMarketPrice() == 0.0)) {
            CurrencyType historicalCurrency =
                historicalQuote.currency() != null ? historicalQuote.currency() : currency;
            asset.setMarketPrice(historicalQuote.price());
            asset.setMarketPriceUsd(
                currencyRateService.convertToBaseCurrency(
                    historicalQuote.price(),
                    BASE_CURRENCY,
                    historicalCurrency,
                    historicalQuote.priceDate()));
            asset.setPriceSource(
                StringUtils.hasText(historicalQuote.priceOrigin())
                    ? historicalQuote.priceOrigin()
                    : "HistoricalPriceFallback");
            asset.setPriceUpdatedAt(now);
            changed.add(asset);
            return;
          }

          if (fallbackSource || asset.getMarketPrice() == null || asset.getMarketPrice() == 0.0) {
            asset.setMarketPrice(weightedPrice.price());
            updated = true;
          }
          if (fallbackSource
              || asset.getMarketPriceUsd() == null
              || asset.getMarketPriceUsd() == 0.0) {
            asset.setMarketPriceUsd(
                currencyRateService.convertToBaseCurrency(
                    weightedPrice.price(), BASE_CURRENCY, currency, rateDate));
            updated = true;
          }
          if (updated) {
            asset.setPriceSource("OpenPositionWeightedAverage");
            asset.setPriceUpdatedAt(now);
            changed.add(asset);
          }
        });

    if (!changed.isEmpty()) {
      assetRepository.saveAll(changed);
      log.info(
          "Seeded {} missing asset prices from open-position weighted averages", changed.size());
    }
  }

  private WeightedPrice weightedOpenPrice(Collection<OpenedPosition> positions) {
    double volume = positions.stream().mapToDouble(position -> nz(position.getVolume())).sum();
    if (volume <= 0.0) {
      return null;
    }
    double weightedValue =
        positions.stream()
            .mapToDouble(position -> nz(position.getOpenPrice()) * nz(position.getVolume()))
            .sum();
    java.util.Set<CurrencyType> currencies =
        positions.stream()
            .map(OpenedPosition::getPriceCurrency)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    // The same instrument can be held across accounts whose reconstruction stamped different
    // position currencies onto price_currency. Do NOT abort the whole backfill for that: a single
    // symbol with mixed/missing currency must not block pricing every other asset (including
    // symbols that have good historical prices). Degrade to an unknown currency and let the
    // downstream historical/asset-currency resolution handle it.
    CurrencyType currency = currencies.size() == 1 ? currencies.iterator().next() : null;
    if (currencies.size() > 1) {
      log.warn(
          "Mixed open-price currencies {} for a reconstructed symbol; deferring to historical/asset"
              + " currency for the weighted-average fallback",
          currencies);
    }
    return new WeightedPrice(weightedValue / volume, currency);
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static HistoricalQuote preferHistoricalQuote(
      HistoricalQuote left, HistoricalQuote right) {
    return Comparator.comparing(HistoricalQuote::priceDate)
                .thenComparing(
                    quote ->
                        quote.qualityScore() == null ? Integer.MIN_VALUE : quote.qualityScore())
                .compare(left, right)
            >= 0
        ? left
        : right;
  }

  private CurrencyType parseCurrency(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      return CurrencyType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private record WeightedPrice(double price, CurrencyType currency) {}

  private record HistoricalQuote(
      BigDecimal price,
      CurrencyType currency,
      String priceOrigin,
      LocalDate priceDate,
      Integer qualityScore) {}
}
