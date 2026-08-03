package com.example.demo.services.dashboard;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.CashOperation;
import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPosition;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetDetailService {

  private static final Set<CashOperationType> DIVIDEND_TYPES =
      Set.of(CashOperationType.DIVIDEND, CashOperationType.WITHHOLDING_TAX);

  private final AssetRepository assetRepository;
  private final OpenedPositionRepository openedPositionRepository;
  private final ClosedPositionRepository closedPositionRepository;
  private final CashOperationRepository cashOperationRepository;

  public AssetDetailView findBySymbol(String rawSymbol) {
    String symbol = normalize(rawSymbol);
    Asset asset =
        assetRepository
            .findBySymbol(symbol)
            .orElseThrow(() -> new AssetDetailNotFoundException(symbol));
    return toView(
        asset,
        openedPositionRepository.findOpenByAssetId(asset.getId()),
        closedPositionRepository.findClosedByAssetId(asset.getId()),
        cashOperationRepository.findAllByAssetIdAndTypeInOrderByDateDescIdDesc(
            asset.getId(), DIVIDEND_TYPES));
  }

  private AssetDetailView toView(
      Asset asset,
      List<OpenedPosition> positions,
      List<ClosedPosition> closedPositions,
      List<CashOperation> dividendOperations) {
    List<AssetHoldingView> holdings = aggregateHoldings(asset, positions);
    List<AssetTransactionView> transactions =
        closedPositions.stream().map(this::toTransaction).toList();
    List<AssetDividendView> dividends = dividendOperations.stream().map(this::toDividend).toList();
    double totalQuantity = holdings.stream().mapToDouble(AssetHoldingView::quantity).sum();
    Double totalMarketValue =
        holdings.stream().allMatch(holding -> holding.marketValue() != null)
            ? holdings.stream().mapToDouble(AssetHoldingView::marketValue).sum()
            : null;
    Double totalUnrealizedProfitLoss =
        holdings.stream().allMatch(holding -> holding.unrealizedProfitLoss() != null)
            ? holdings.stream().mapToDouble(AssetHoldingView::unrealizedProfitLoss).sum()
            : null;
    Double totalRealizedProfitLoss =
        transactions.stream().allMatch(transaction -> transaction.realizedProfitLoss() != null)
            ? transactions.stream().mapToDouble(AssetTransactionView::realizedProfitLoss).sum()
            : null;
    double totalGrossDividends =
        dividends.stream()
            .filter(dividend -> dividend.type() == CashOperationType.DIVIDEND)
            .mapToDouble(dividend -> safe(dividend.amount()))
            .sum();
    double totalWithholdingTax =
        dividends.stream()
            .filter(dividend -> dividend.type() == CashOperationType.WITHHOLDING_TAX)
            .mapToDouble(dividend -> Math.abs(safe(dividend.amount())))
            .sum();

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
        totalUnrealizedProfitLoss,
        transactions,
        totalRealizedProfitLoss,
        dividends,
        totalGrossDividends,
        totalWithholdingTax,
        totalGrossDividends - totalWithholdingTax);
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

  private AssetTransactionView toTransaction(ClosedPosition position) {
    return new AssetTransactionView(
        position.getId(),
        position.getAccount(),
        position.getType(),
        position.signedQuantity(),
        position.getOpenTime(),
        position.getCloseTime(),
        position.getOpenPrice(),
        position.getClosePrice(),
        position.getCommission(),
        position.getCommissionCurrency(),
        position.getProfit(),
        position.getProfitCurrency(),
        position.getSourcePositionId(),
        position.getBrokerSymbol());
  }

  private AssetDividendView toDividend(CashOperation operation) {
    return new AssetDividendView(
        operation.getId(),
        operation.getAccount(),
        operation.getType(),
        operation.getDate(),
        operation.getAmount(),
        operation.getCurrency(),
        operation.getComment());
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
