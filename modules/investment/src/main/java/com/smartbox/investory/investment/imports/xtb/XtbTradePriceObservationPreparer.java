package com.smartbox.investory.investment.imports.xtb;

import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Aggregates XTB trade price observations without performing persistence. */
class XtbTradePriceObservationPreparer {

  Map<PriceCheckpointKey, WeightedPrice> prepare(
      Iterable<PositionEntity> closedPositions,
      Iterable<PositionEntity> openedPositions,
      CurrencyType defaultCurrency,
      Map<String, CurrencyType> quoteCurrencyBySymbol,
      TradeObservationCurrencyResolver currencyResolver) {
    Map<PriceCheckpointKey, WeightedPrice> aggregated = new LinkedHashMap<>();

    for (PositionEntity position : closedPositions) {
      CurrencyType quoteCurrency =
          currencyResolver.resolve(
              position.getSymbol(),
              position.getPriceCurrency(),
              defaultCurrency,
              quoteCurrencyBySymbol);
      BigDecimal normalizedOpenPrice =
          normalizeTradeObservationPrice(
              position.getOpenPrice(), position.getPurchaseValue(), position.getVolume());
      BigDecimal normalizedClosePrice =
          normalizeTradeObservationPrice(
              position.getClosePrice(), position.getSaleValue(), position.getVolume());
      addCheckpoint(
          aggregated,
          position.getSymbol(),
          position.getOpenTime(),
          normalizedOpenPrice,
          position.getVolume(),
          quoteCurrency,
          "XTB_TRADE_OPEN",
          "XTB_TRADE_OPEN",
          "XTB_TRADE_OPEN_OBSERVATION");
      addCheckpoint(
          aggregated,
          position.getSymbol(),
          position.getCloseTime(),
          normalizedClosePrice,
          position.getVolume(),
          quoteCurrency,
          "XTB_TRADE_CLOSE",
          "XTB_TRADE_CLOSE",
          "XTB_TRADE_CLOSE_OBSERVATION");
    }

    for (PositionEntity position : openedPositions) {
      CurrencyType quoteCurrency =
          currencyResolver.resolve(
              position.getSymbol(),
              position.getPriceCurrency(),
              defaultCurrency,
              quoteCurrencyBySymbol);
      BigDecimal normalizedOpenPrice =
          normalizeTradeObservationPrice(
              position.getOpenPrice(), position.getPurchaseValue(), position.getVolume());
      addCheckpoint(
          aggregated,
          position.getSymbol(),
          position.getOpenTime(),
          normalizedOpenPrice,
          position.getVolume(),
          quoteCurrency,
          "XTB_TRADE_OPEN",
          "XTB_TRADE_OPEN",
          "XTB_TRADE_OPEN_OBSERVATION");
    }
    return aggregated;
  }

  private void addCheckpoint(
      Map<PriceCheckpointKey, WeightedPrice> aggregated,
      String symbol,
      java.time.ZonedDateTime timestamp,
      BigDecimal price,
      BigDecimal volume,
      CurrencyType currency,
      String source,
      String priceOrigin,
      String qualityClass) {
    if (!StringUtils.hasText(symbol) || timestamp == null || price == null || price.signum() <= 0) {
      return;
    }
    BigDecimal weight = volume == null ? BigDecimal.ZERO : volume.abs();
    if (weight.signum() <= 0) {
      weight = BigDecimal.ONE;
    }
    PriceCheckpointKey key =
        new PriceCheckpointKey(
            symbol,
            timestamp.toLocalDate(),
            java.util.Objects.requireNonNull(currency, "XTB trade price currency"),
            source,
            priceOrigin,
            qualityClass);
    aggregated.computeIfAbsent(key, ignored -> new WeightedPrice()).add(price, weight);
  }

  private BigDecimal normalizeTradeObservationPrice(
      BigDecimal rawPrice, BigDecimal grossValue, BigDecimal volume) {
    if (rawPrice == null || rawPrice.signum() <= 0) {
      return rawPrice;
    }
    if (grossValue == null || volume == null || volume.abs().signum() == 0) {
      return rawPrice;
    }

    BigDecimal effectivePrice = grossValue.divide(volume, 16, RoundingMode.HALF_UP).abs();
    if (effectivePrice.signum() <= 0) {
      return rawPrice;
    }

    BigDecimal ratio = rawPrice.divide(effectivePrice, 16, RoundingMode.HALF_UP);
    if (approximately(ratio, BigDecimal.TEN)
        || approximately(ratio, BigDecimal.valueOf(100))
        || approximately(ratio, BigDecimal.valueOf(1000))) {
      return effectivePrice;
    }
    if (approximately(ratio, new BigDecimal("0.1"))
        || approximately(ratio, new BigDecimal("0.01"))
        || approximately(ratio, new BigDecimal("0.001"))) {
      return effectivePrice;
    }
    return rawPrice;
  }

  private boolean approximately(BigDecimal value, BigDecimal target) {
    return value.subtract(target).abs().compareTo(target.abs().multiply(new BigDecimal("0.05")))
        <= 0;
  }

  record PriceCheckpointKey(
      String symbol,
      java.time.LocalDate priceDate,
      CurrencyType currency,
      String source,
      String priceOrigin,
      String qualityClass) {}

  static final class WeightedPrice {
    private BigDecimal weightedPriceSum = BigDecimal.ZERO;
    private BigDecimal totalWeight = BigDecimal.ZERO;

    private void add(BigDecimal price, BigDecimal weight) {
      weightedPriceSum = weightedPriceSum.add(price.multiply(weight));
      totalWeight = totalWeight.add(weight);
    }

    BigDecimal weightedAverage() {
      return totalWeight.signum() <= 0
          ? BigDecimal.ZERO
          : weightedPriceSum.divide(totalWeight, 16, RoundingMode.HALF_UP);
    }

    BigDecimal totalWeight() {
      return totalWeight;
    }
  }

  @FunctionalInterface
  interface TradeObservationCurrencyResolver {
    CurrencyType resolve(
        String symbol,
        CurrencyType positionCurrency,
        CurrencyType defaultCurrency,
        Map<String, CurrencyType> quoteCurrencyBySymbol);
  }
}
