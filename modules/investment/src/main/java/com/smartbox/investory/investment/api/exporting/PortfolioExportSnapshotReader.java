package com.smartbox.investory.investment.api.exporting;

import java.math.BigDecimal;
import java.util.List;

/** Typed read boundary for secondary portfolio exporters. */
public interface PortfolioExportSnapshotReader {
  PortfolioExportSnapshot currentSnapshot(Long portfolioId);

  record PortfolioExportSnapshot(
      List<ExportPosition> positions, List<ExportCashBalance> cashBalances) {
    public PortfolioExportSnapshot {
      positions =
          com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(positions);
      cashBalances =
          com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(cashBalances);
    }
  }

  record ExportPosition(
      Long accountId,
      String symbol,
      BigDecimal quantity,
      BigDecimal openPrice,
      BigDecimal marketPrice) {}

  record ExportCashBalance(Long accountId, BigDecimal amount) {}
}
