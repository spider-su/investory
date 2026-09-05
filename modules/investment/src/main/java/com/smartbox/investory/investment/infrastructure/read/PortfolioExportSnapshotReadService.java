package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.ExportCashBalance;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.ExportPosition;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.PortfolioExportSnapshot;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
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

  @Override
  public PortfolioExportSnapshot currentSnapshot(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    var accountIds =
        accounts.findAllByPortfolioId(portfolioId).stream()
            .map(account -> account.getId())
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    var openPositions = positions.findOpenByAccountIn(accountIds);
    Map<Long, AssetEntity> assetsById =
        assets
            .findAllById(
                openPositions.stream()
                    .map(position -> position.getAssetId())
                    .filter(java.util.Objects::nonNull)
                    .toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(AssetEntity::getId, asset -> asset));
    Map<Long, BigDecimal> bondCashByAccount = new HashMap<>();
    List<ExportPosition> exportPositions = new ArrayList<>();
    for (var position : openPositions) {
      AssetEntity asset = assetsById.get(position.getAssetId());
      if (isBond(asset)) {
        BigDecimal price = exportMarketPrice(position, assetsById);
        if (price == null) {
          price = position.getOpenPrice();
        }
        if (price != null && position.getVolume() != null) {
          bondCashByAccount.merge(
              position.getAccount(), position.getVolume().abs().multiply(price), BigDecimal::add);
        }
      } else {
        exportPositions.add(
            new ExportPosition(
                position.getAccount(),
                position.getSymbol(),
                position.getVolume(),
                position.getOpenPrice(),
                exportMarketPrice(position, assetsById)));
      }
    }

    List<ExportCashBalance> exportCashBalances = new ArrayList<>();
    for (var statistic : statistics.findAllByAccountIdIn(accountIds)) {
      if (statistic.getCashBalance() != null) {
        exportCashBalances.add(
            new ExportCashBalance(statistic.getAccountId(), statistic.getCashBalance()));
      }
    }
    bondCashByAccount.forEach(
        (accountId, amount) -> exportCashBalances.add(new ExportCashBalance(accountId, amount)));
    return new PortfolioExportSnapshot(exportPositions, exportCashBalances);
  }

  private static boolean isBond(AssetEntity asset) {
    return asset != null && "BOND".equalsIgnoreCase(asset.getAssetType());
  }

  private static BigDecimal exportMarketPrice(
      com.smartbox.investory.investment.ledger.position.persistence.PositionEntity position,
      Map<Long, AssetEntity> assetsById) {
    AssetEntity asset = assetsById.get(position.getAssetId());
    BigDecimal price = position.getMarketPrice();
    if (price == null && asset != null) {
      price = asset.getMarketPrice();
    }
    if (price != null && asset != null && "BOND".equalsIgnoreCase(asset.getAssetType())) {
      return price.movePointLeft(2);
    }
    return price;
  }
}
