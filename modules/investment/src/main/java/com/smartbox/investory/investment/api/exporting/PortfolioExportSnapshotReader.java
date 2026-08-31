package com.smartbox.investory.investment.api.exporting;

import java.math.BigDecimal;
import java.util.List;

/** Typed read boundary for secondary portfolio exporters. */
public interface PortfolioExportSnapshotReader {
  PortfolioExportSnapshot currentSnapshot();

  record PortfolioExportSnapshot(
      List<ExportPosition> positions, List<ExportCashBalance> cashBalances) {
    public PortfolioExportSnapshot {
      positions = positions == null ? List.of() : List.copyOf(positions);
      cashBalances = cashBalances == null ? List.of() : List.copyOf(cashBalances);
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
