package com.example.demo.services.dashboard;

import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetDetailService {

  private final AssetRepository assetRepository;
  private final OpenedPositionRepository openedPositionRepository;

  public AssetDetailView findBySymbol(String rawSymbol) {
    String symbol = normalize(rawSymbol);
    Asset asset =
        assetRepository
            .findBySymbol(symbol)
            .orElseThrow(() -> new AssetDetailNotFoundException(symbol));
    return toView(asset, openedPositionRepository.findOpenByAssetId(asset.getId()));
  }

  private AssetDetailView toView(Asset asset, List<OpenedPosition> positions) {
    List<AssetHoldingView> holdings = aggregateHoldings(asset, positions);
    double totalQuantity = holdings.stream().mapToDouble(AssetHoldingView::quantity).sum();
    Double totalMarketValue =
        holdings.stream().allMatch(holding -> holding.marketValue() != null)
            ? holdings.stream().mapToDouble(AssetHoldingView::marketValue).sum()
            : null;
    Double totalUnrealizedProfitLoss =
        holdings.stream().allMatch(holding -> holding.unrealizedProfitLoss() != null)
            ? holdings.stream().mapToDouble(AssetHoldingView::unrealizedProfitLoss).sum()
            : null;

    return new AssetDetailView(
        asset.getId(),
        asset.getSymbol(),
        asset.getName(),
        asset.getTicker(),
        asset.getYahoo(),
        asset.getAssetType(),
        asset.getCountry(),
        asset.getCurrency(),
        asset.getMarketPrice(),
        asset.getMarketPriceUsd(),
        asset.getPriceSource(),
        asset.getPriceUpdatedAt(),
        holdings,
        totalQuantity,
        totalMarketValue,
        totalUnrealizedProfitLoss);
  }

  private List<AssetHoldingView> aggregateHoldings(Asset asset, List<OpenedPosition> positions) {
    Map<Long, List<OpenedPosition>> byAccount =
        positions.stream().collect(Collectors.groupingBy(OpenedPosition::getAccount));

    return byAccount.entrySet().stream()
        .map(entry -> toHolding(asset, entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(AssetHoldingView::accountId))
        .toList();
  }

  private AssetHoldingView toHolding(Asset asset, Long accountId, List<OpenedPosition> positions) {
    double quantity = positions.stream().mapToDouble(OpenedPosition::signedQuantity).sum();
    double absoluteQuantity =
        positions.stream().mapToDouble(position -> Math.abs(position.signedQuantity())).sum();
    double weightedCost =
        positions.stream()
            .mapToDouble(
                position -> Math.abs(position.signedQuantity()) * safe(position.getOpenPrice()))
            .sum();
    double averageCost = absoluteQuantity == 0 ? 0 : weightedCost / absoluteQuantity;
    Double marketPrice = asset.getMarketPrice();
    Double marketValue = marketPrice == null ? null : quantity * marketPrice;
    Double unrealizedProfitLoss =
        marketPrice == null ? null : quantity * (marketPrice - averageCost);
    OpenedPosition first = positions.getFirst();

    return new AssetHoldingView(
        accountId,
        quantity,
        averageCost,
        first.getCostCurrency(),
        marketPrice,
        first.getPriceCurrency(),
        marketValue,
        unrealizedProfitLoss,
        first.getSettlementModel());
  }

  private double safe(Double value) {
    return value == null ? 0 : value;
  }

  private String normalize(String rawSymbol) {
    if (rawSymbol == null || rawSymbol.isBlank()) {
      throw new AssetDetailNotFoundException("");
    }
    return rawSymbol.trim().toUpperCase(Locale.ROOT);
  }
}
