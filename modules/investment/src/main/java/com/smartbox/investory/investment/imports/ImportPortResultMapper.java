package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.port.importing.BrokerImportResult;

/** Maps the Investment import result to the public broker-import port result. */
public final class ImportPortResultMapper {
  private ImportPortResultMapper() {}

  public static BrokerImportResult toPortResult(ImportExecutionResult result) {
    return new BrokerImportResult(
        result.rowsTotal(),
        result.rowsApplied(),
        result.rowsFailed(),
        result.details(),
        result.affectedAccountIds());
  }
}
