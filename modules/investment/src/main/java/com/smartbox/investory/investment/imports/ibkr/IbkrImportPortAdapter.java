package com.smartbox.investory.investment.imports.ibkr;

import com.smartbox.investory.investment.imports.ImportExecutionResult;
import com.smartbox.investory.investment.port.importing.BrokerImportResult;
import com.smartbox.investory.investment.port.importing.IbkrImportPort;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps the provider adapter behind the public import port while ledger writes stay in Investment.
 */
@Component
@RequiredArgsConstructor
final class IbkrImportPortAdapter implements IbkrImportPort {
  private final IbkrImportService importService;

  @Override
  public BrokerImportResult importStatement(InputStream inputStream, String fileName)
      throws Exception {
    return toPortResult(importService.importStatement(inputStream, fileName));
  }

  private static BrokerImportResult toPortResult(ImportExecutionResult result) {
    return new BrokerImportResult(
        result.rowsTotal(), result.rowsApplied(), result.rowsFailed(), result.details());
  }
}
