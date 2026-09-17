package com.smartbox.investory.investment.valuation.price;

import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Legacy manual-price flow. It intentionally updates only the asset's current manual price fields;
 * deprecated manual updates do not rebuild projections, refresh statistics, synchronize IBKR
 * positions, or write {@code MANUAL} observations to {@code asset_price_history}.
 */
@Service
@RequiredArgsConstructor
public class ManualAssetPriceService {

  private static final CurrencyType BASE_CURRENCY = FinancialPolicyDefaults.CANONICAL_CURRENCY;

  private final AssetRepository assetRepository;
  private final CurrencyRateService currencyRateService;
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

    BigDecimal normalizedPrice = normalizePrice(asset, marketPrice);
    CurrencyType currency = asset.getCurrency() != null ? asset.getCurrency() : BASE_CURRENCY;
    BigDecimal marketPriceUsd =
        currency == BASE_CURRENCY
            ? normalizedPrice
            : currencyRateService.convertToBaseCurrency(
                normalizedPrice, BASE_CURRENCY, currency, applicationTime.today());

    ZonedDateTime updatedAt = applicationTime.now(applicationTime.businessZone());
    asset.setMarketPrice(normalizedPrice);
    asset.setMarketPriceUsd(marketPriceUsd);
    asset.setPriceSource("Manual");
    asset.setPriceUpdatedAt(updatedAt);
    assetRepository.save(asset);
    return new ManualAssetPrice(
        asset.getSymbol(),
        normalizedPrice,
        marketPriceUsd,
        currency,
        asset.getPriceSource(),
        updatedAt);
  }

  private static BigDecimal normalizePrice(AssetEntity asset, BigDecimal quotedPrice) {
    return "BOND".equalsIgnoreCase(asset.getAssetType())
            && quotedPrice.compareTo(BigDecimal.TEN) > 0
        ? quotedPrice.movePointLeft(2)
        : quotedPrice;
  }

  public record ManualAssetPrice(
      String symbol,
      BigDecimal marketPrice,
      BigDecimal marketPriceUsd,
      CurrencyType currency,
      String source,
      ZonedDateTime updatedAt) {}
}
