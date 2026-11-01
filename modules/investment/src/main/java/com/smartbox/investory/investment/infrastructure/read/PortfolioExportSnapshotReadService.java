package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.ExportCashBalance;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.ExportPosition;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.PortfolioExportSnapshot;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.position.CurrentOpenPositionValueEntity;
import com.smartbox.investory.investment.infrastructure.persistence.position.CurrentOpenPositionValueRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioExportSnapshotReadService implements PortfolioExportSnapshotReader {
  private final PositionRepository positions;
  private final AccountStatisticsRepository statistics;
  private final AccountRepository accounts;
  private final AssetRepository assets;
  private final CurrentOpenPositionValueRepository valuations;
  private final CurrencyConversion currencyConversion;
  private final ApplicationTime applicationTime;

  @Override
  public PortfolioExportSnapshot currentSnapshot(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    var accountIds =
        accounts.findAllByPortfolioId(portfolioId).stream()
            .filter(account -> !account.isCashOnly())
            .map(AccountEntity::getId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    if (accountIds.isEmpty()) {
      return new PortfolioExportSnapshot(List.of(), List.of());
    }
    var openPositions = positions.findOpenByAccountIn(accountIds);
    Map<Long, CurrentOpenPositionValueEntity> valuesByPosition =
        valuations.findAllByPortfolioId(portfolioId).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    CurrentOpenPositionValueEntity::getPositionId, value -> value));
    Map<Long, AssetEntity> assetsById =
        assets
            .findAllById(
                openPositions.stream()
                    .map(PositionEntity::getAssetId)
                    .filter(java.util.Objects::nonNull)
                    .toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(AssetEntity::getId, asset -> asset));
    Map<Long, BigDecimal> bondCashByAccount = new HashMap<>();
    List<ExportPosition> exportPositions = new ArrayList<>();
    for (var position : openPositions) {
      AssetEntity asset = assetsById.get(position.getAssetId());
      if (asset == null) {
        throw new IllegalStateException("Missing export asset for position " + position.getId());
      }
      var value = valuesByPosition.get(position.getId());
      if (value == null) {
        throw new IllegalStateException(
            "Missing canonical export valuation for position " + position.getId());
      }
      if (isBond(asset)) {
        BigDecimal rateToBase = value.getMarketPriceToBaseRate();
        if (value.getMarketValueInBaseCurrency() == null
            || value.getMarketPriceCurrency() == null
            || rateToBase == null
            || rateToBase.signum() <= 0) {
          throw new IllegalStateException(
              "Incomplete export bond valuation for position " + position.getId());
        }
        // Reverse the exact FX rate used by the canonical valuation rather than applying
        // an independently resolved reverse rate (which could distort the USD principal).
        BigDecimal bondInQuoteCurrency =
            value.getMarketValueInBaseCurrency().divide(rateToBase, MathContext.DECIMAL128);
        CurrencyType quoteCurrency = CurrencyType.valueOf(value.getMarketPriceCurrency());
        BigDecimal bondInUsd =
            quoteCurrency == CurrencyType.USD
                ? bondInQuoteCurrency
                : currencyConversion.convertToBaseCurrency(
                    bondInQuoteCurrency,
                    CurrencyType.USD,
                    quoteCurrency,
                    applicationTime.today());
        bondCashByAccount.merge(position.getAccount(), bondInUsd, BigDecimal::add);
      } else {
        if (value.getMarketPrice() == null
            || asset.getCurrency() == null
            || !asset.getCurrency().name().equals(value.getMarketPriceCurrency())) {
          throw new IllegalStateException(
              "Missing or incompatible canonical export price for position " + position.getId());
        }
        exportPositions.add(
            new ExportPosition(
                position.getAccount(),
                position.getSymbol(),
                position.getVolume(),
                position.getOpenPrice(),
                value.getMarketPrice()));
      }
    }

    List<ExportCashBalance> exportCashBalances = new ArrayList<>();
    var accountStatistics = statistics.findAllByAccountIdIn(accountIds);
    var reportedAccounts =
        accountStatistics.stream()
            .map(statistic -> statistic.getAccountId())
            .collect(java.util.stream.Collectors.toSet());
    if (!reportedAccounts.equals(accountIds)) {
      throw new IllegalStateException(
          "Missing export cash statistics for portfolio " + portfolioId);
    }
    for (var statistic : accountStatistics) {
      if (statistic.getCashBalance() == null || statistic.getValuationCurrency() == null) {
        throw new IllegalStateException(
            "Incomplete export cash valuation for account " + statistic.getAccountId());
      }
      // Cash statistics are in portfolio base currency. Bond value has already been restored
      // to its USD amount using the canonical valuation's original FX rate.
      BigDecimal cashInUsd =
          currencyConversion.convertToBaseCurrency(
              statistic.getCashBalance(),
              CurrencyType.USD,
              CurrencyType.valueOf(statistic.getValuationCurrency()),
              applicationTime.today());
      BigDecimal totalInUsd =
          cashInUsd.add(bondCashByAccount.getOrDefault(statistic.getAccountId(), BigDecimal.ZERO));
      exportCashBalances.add(new ExportCashBalance(statistic.getAccountId(), totalInUsd));
    }
    return new PortfolioExportSnapshot(exportPositions, exportCashBalances);
  }

  private static boolean isBond(AssetEntity asset) {
    return asset != null && "BOND".equalsIgnoreCase(asset.getAssetType());
  }
}
