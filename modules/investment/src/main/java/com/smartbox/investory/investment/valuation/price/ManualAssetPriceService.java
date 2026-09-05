package com.smartbox.investory.investment.valuation.price;

import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.projection.StatisticsRefreshService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ManualAssetPriceService {

  private static final CurrencyType BASE_CURRENCY = FinancialPolicyDefaults.CANONICAL_CURRENCY;

  private final AssetRepository assetRepository;
  private final AssetPriceHistoryRepository assetPriceHistoryRepository;
  private final CurrencyRateService currencyRateService;
  private final MarketDataService marketDataService;
  private final StatisticsRefreshService statisticsRefreshService;
  private final ApplicationTime applicationTime;

  @Transactional
  public ManualAssetPrice updatePrice(String symbol, BigDecimal marketPrice) {
    if (!StringUtils.hasText(symbol)) {
      throw new IllegalArgumentException("AssetEntity symbol is required");
    }
    if (marketPrice == null || marketPrice.signum() <= 0) {
      throw new IllegalArgumentException("Market price must be positive");
    }

    AssetEntity asset =
        assetRepository
            .findBySymbol(symbol)
            .orElseThrow(() -> new IllegalArgumentException("AssetEntity not found: " + symbol));
    if (Boolean.TRUE.equals(asset.getExcludeFromImport())) {
      throw new IllegalArgumentException(
          "AssetEntity is excluded from Investory calculations: " + symbol);
    }

    CurrencyType currency = asset.getCurrency() != null ? asset.getCurrency() : BASE_CURRENCY;
    BigDecimal marketPriceUsd =
        currency == BASE_CURRENCY
            ? marketPrice
            : currencyRateService.convertToBaseCurrency(
                marketPrice, BASE_CURRENCY, currency, applicationTime.today());

    ZonedDateTime updatedAt = applicationTime.now(applicationTime.businessZone());
    asset.setMarketPrice(marketPrice);
    asset.setMarketPriceUsd(marketPriceUsd);
    asset.setPriceSource("Manual");
    asset.setPriceUpdatedAt(updatedAt);
    assetRepository.save(asset);
    assetPriceHistoryRepository.upsertObservedPrice(
        asset.getId(),
        applicationTime.today(),
        "MANUAL",
        asset.getSymbol(),
        asset.getSymbol(),
        "MANUAL",
        currency.name(),
        marketPrice,
        100,
        "MANUAL");

    marketDataService.syncIbkrPositions();
    refreshStatisticsAfterCommit();

    return new ManualAssetPrice(
        asset.getSymbol(),
        marketPrice,
        marketPriceUsd,
        currency,
        asset.getPriceSource(),
        updatedAt);
  }

  private void refreshStatisticsAfterCommit() {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      statisticsRefreshService.refreshAll();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            statisticsRefreshService.refreshAllAfterCommittedMutation();
          }
        });
  }

  public record ManualAssetPrice(
      String symbol,
      BigDecimal marketPrice,
      BigDecimal marketPriceUsd,
      CurrencyType currency,
      String source,
      ZonedDateTime updatedAt) {}
}
