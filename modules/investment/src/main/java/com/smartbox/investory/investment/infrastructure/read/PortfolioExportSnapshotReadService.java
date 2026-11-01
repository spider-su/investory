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
import java.math.BigDecimal;
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
        if (value.getMarketValueInBaseCurrency() == null) {
          throw new IllegalStateException(
              "Incomplete export bond valuation for position " + position.getId());
        }
        bondCashByAccount.merge(
            position.getAccount(), value.getMarketValueInBaseCurrency(), BigDecimal::add);
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
      if (statistic.getCashBalance() == null) {
        throw new IllegalStateException(
            "Incomplete export cash balance for account " + statistic.getAccountId());
      }
      exportCashBalances.add(
          new ExportCashBalance(statistic.getAccountId(), statistic.getCashBalance()));
    }
    bondCashByAccount.forEach(
        (accountId, amount) -> exportCashBalances.add(new ExportCashBalance(accountId, amount)));
    return new PortfolioExportSnapshot(exportPositions, exportCashBalances);
  }

  private static boolean isBond(AssetEntity asset) {
    return asset != null && "BOND".equalsIgnoreCase(asset.getAssetType());
  }
}
