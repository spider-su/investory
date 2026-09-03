package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.ExportCashBalance;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.ExportPosition;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.PortfolioExportSnapshot;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountRepository;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
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
    return new PortfolioExportSnapshot(
        positions.findAllByAccountIn(accountIds).stream()
            .map(
                position ->
                    new ExportPosition(
                        position.getAccount(),
                        position.getSymbol(),
                        position.getVolume(),
                        position.getOpenPrice(),
                        position.getMarketPrice()))
            .toList(),
        statistics.findAllByAccountIdIn(accountIds).stream()
            .filter(statistic -> statistic.getCashBalance() != null)
            .map(
                statistic ->
                    new ExportCashBalance(statistic.getAccountId(), statistic.getCashBalance()))
            .toList());
  }
}
