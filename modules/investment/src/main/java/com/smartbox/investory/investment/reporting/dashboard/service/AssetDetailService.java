package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.accounting.CashOperationType;
import com.smartbox.investory.investment.infrastructure.persistence.AssetEntity;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
import com.smartbox.investory.investment.infrastructure.persistence.CashOperationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.CashOperationRepository;
import com.smartbox.investory.investment.infrastructure.persistence.ClosedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.ClosedPositionRepository;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPosition;
import com.smartbox.investory.investment.infrastructure.persistence.OpenedPositionRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.SymbolPerformanceRepository;
import com.smartbox.investory.investment.market.price.YahooSymbolResolver;
import java.time.ZonedDateTime;
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
  private final SymbolPerformanceRepository symbolPerformanceRepository;

  public AssetDetailView findBySymbol(String rawSymbol) {
    return findBySymbol(rawSymbol, DashboardPeriod.ONE_YEAR);
  }

  public AssetDetailView findBySymbol(String rawSymbol, DashboardPeriod period) {
    String symbol = normalize(rawSymbol);
    AssetEntity asset =
        assetRepository
            .findBySymbol(symbol)
            .orElseThrow(() -> new AssetDetailNotFoundException(symbol));
    ZonedDateTime startDate = period.startDate(ZonedDateTime.now());
    return toView(
        asset,
        openedPositionRepository.findOpenByAssetId(asset.getId()),
        filterClosedPositions(
            closedPositionRepository.findClosedByAssetId(asset.getId()), startDate),
        filterDividendOperations(
            cashOperationRepository.findAllByAssetIdAndTypeInOrderByDateDescIdDesc(
                asset.getId(), DIVIDEND_TYPES),
            startDate),
        period,
        aggregatePerformance(symbolPerformanceRepository.findAllBySymbol(asset.getSymbol())));
  }

  private AssetDetailView toView(
      AssetEntity asset,
      List<OpenedPosition> positions,
      List<ClosedPosition> closedPositions,
      List<CashOperationEntity> dividendOperations,
      DashboardPeriod period,
      AssetPerformanceView performance) {
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
        YahooSymbolResolver.resolve(asset.getSymbol(), asset.getYahoo()),
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
        totalGrossDividends - totalWithholdingTax,
        period,
        performance);
  }

  private List<ClosedPosition> filterClosedPositions(
      List<ClosedPosition> positions, ZonedDateTime startDate) {
    if (startDate == null) {
      return positions;
    }
    return positions.stream()
        .filter(position -> position.getCloseTime() != null)
        .filter(position -> !position.getCloseTime().isBefore(startDate))
        .toList();
  }

  private List<CashOperationEntity> filterDividendOperations(
      List<CashOperationEntity> operations, ZonedDateTime startDate) {
    if (startDate == null) {
      return operations;
    }
    return operations.stream()
        .filter(operation -> operation.getDate() != null)
        .filter(operation -> !operation.getDate().isBefore(startDate))
        .toList();
  }

  private AssetPerformanceView aggregatePerformance(List<SymbolPerformanceEntity> rows) {
    if (rows.isEmpty()) {
      return null;
    }
    return new AssetPerformanceView(
        rows.stream().mapToDouble(row -> safe(row.getClosedProfit())).sum(),
        rows.stream().mapToDouble(row -> safe(row.getUnrealizedProfit())).sum(),
        rows.stream().mapToDouble(row -> safe(row.getTotalProfit())).sum(),
        rows.stream().mapToDouble(row -> safe(row.getDividends())).sum(),
        rows.stream().mapToDouble(row -> safe(row.getWithholdingTax())).sum(),
        rows.stream().mapToDouble(row -> safe(row.getCostBasis())).sum(),
        rows.stream().mapToDouble(row -> safe(row.getMarketValue())).sum(),
        rows.stream()
            .map(SymbolPerformanceEntity::getUpdatedAt)
            .filter(value -> value != null)
            .max(ZonedDateTime::compareTo)
            .orElse(null));
  }

  private List<AssetHoldingView> aggregateHoldings(AssetEntity asset, List<OpenedPosition> positions) {
    Map<Long, List<OpenedPosition>> byAccount =
        positions.stream().collect(Collectors.groupingBy(OpenedPosition::getAccount));

    return byAccount.entrySet().stream()
        .map(entry -> toHolding(asset, entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(AssetHoldingView::accountId))
        .toList();
  }

  private AssetHoldingView toHolding(AssetEntity asset, Long accountId, List<OpenedPosition> positions) {
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

  private AssetDividendView toDividend(CashOperationEntity operation) {
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
