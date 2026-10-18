package com.smartbox.investory.investment.imports.xtb;

import com.smartbox.investory.investment.imports.ImportPortResultMapper;
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
    return ImportPortResultMapper.toPortResult(importService.importZip(inputStream, sourceName));
  }

  @Override
  public BrokerImportResult importWorkbook(InputStream inputStream, String sourceName)
      throws Exception {
    return ImportPortResultMapper.toPortResult(
        importService.importWorkbook(inputStream, sourceName));
  }
}
