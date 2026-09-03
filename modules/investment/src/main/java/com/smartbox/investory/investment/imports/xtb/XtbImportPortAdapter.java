package com.smartbox.investory.investment.imports.xtb;

import com.smartbox.investory.investment.imports.ImportExecutionResult;
import com.smartbox.investory.investment.port.importing.BrokerImportResult;
import com.smartbox.investory.investment.port.importing.XtbImportPort;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps the provider adapter behind the public import port while ledger writes stay in Investment.
 */
@Component
@RequiredArgsConstructor
final class XtbImportPortAdapter implements XtbImportPort {
  private final XtbImportService importService;

  @Override
  public boolean isZipReport(String fileName) {
    return importService.isZipReport(fileName);
  }

  @Override
  public boolean supports(InputStream inputStream) {
    return importService.supports(inputStream);
  }

  @Override
  public BrokerImportResult importZip(InputStream inputStream, String sourceName) throws Exception {
    return toPortResult(importService.importZip(inputStream, sourceName));
  }

  @Override
  public BrokerImportResult importWorkbook(InputStream inputStream, String sourceName)
      throws Exception {
    return toPortResult(importService.importWorkbook(inputStream, sourceName));
  }

  private static BrokerImportResult toPortResult(ImportExecutionResult result) {
    return new BrokerImportResult(
        result.rowsTotal(), result.rowsApplied(), result.rowsFailed(), result.details());
  }
}
