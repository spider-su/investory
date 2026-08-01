package com.example.demo.services;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.services.currency.CurrencyRateService;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ManualAssetPriceService {

  private static final CurrencyType BASE_CURRENCY = CurrencyType.USD;

  private final AssetRepository assetRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final CurrencyRateService currencyRateService;
  private final MarketService marketService;
  private final StatisticsRefreshService statisticsRefreshService;

  @Transactional
  public ManualAssetPrice updatePrice(String symbol, double marketPrice) {
    if (!StringUtils.hasText(symbol)) {
      throw new IllegalArgumentException("Asset symbol is required");
    }
    if (!Double.isFinite(marketPrice) || marketPrice <= 0.0) {
      throw new IllegalArgumentException("Market price must be positive");
    }

    Asset asset =
        assetRepository
            .findBySymbol(symbol)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + symbol));

    CurrencyType currency = asset.getCurrency() != null ? asset.getCurrency() : BASE_CURRENCY;
    double marketPriceUsd =
        currency == BASE_CURRENCY
            ? marketPrice
            : currencyRateService.convertToBaseCurrency(
                marketPrice, BASE_CURRENCY, currency, LocalDate.now());

    ZonedDateTime updatedAt = ZonedDateTime.now();
    asset.setMarketPrice(marketPrice);
    asset.setMarketPriceUsd(marketPriceUsd);
    asset.setPriceSource("Manual");
    asset.setPriceUpdatedAt(updatedAt);
    assetRepository.save(asset);
    assetPriceHistoryRepository.upsertObservedPrice(
        asset.getId(),
        ReportingDateHelper.today(),
        "MANUAL",
        asset.getSymbol(),
        asset.getSymbol(),
        "MANUAL",
        currency.name(),
        marketPrice,
        100,
        "MANUAL");

    marketService.syncStocks();
    statisticsRefreshService.refreshAll();

    return new ManualAssetPrice(
        asset.getSymbol(), marketPrice, marketPriceUsd, currency, asset.getPriceSource(), updatedAt);
  }

  public record ManualAssetPrice(
      String symbol,
      double marketPrice,
      double marketPriceUsd,
      CurrencyType currency,
      String source,
      ZonedDateTime updatedAt) {}
}
