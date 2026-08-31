package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.ExportCashBalance;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.ExportPosition;
import com.smartbox.investory.investment.api.exporting.PortfolioExportSnapshotReader.PortfolioExportSnapshot;
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

  @Override
  public PortfolioExportSnapshot currentSnapshot() {
    return new PortfolioExportSnapshot(
        positions.findAll().stream()
            .map(
                position ->
                    new ExportPosition(
                        position.getAccount(),
                        position.getSymbol(),
                        position.getVolume(),
                        position.getOpenPrice(),
                        position.getMarketPrice()))
            .toList(),
        statistics.findAll().stream()
            .filter(statistic -> statistic.getCashBalance() != null)
            .map(
                statistic ->
                    new ExportCashBalance(statistic.getAccountId(), statistic.getCashBalance()))
            .toList());
  }
}
