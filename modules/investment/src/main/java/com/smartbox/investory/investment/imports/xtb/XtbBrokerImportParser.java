package com.smartbox.investory.investment.imports.xtb;

import com.smartbox.investory.investment.imports.BrokerImportParser;
import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportExecutionResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XtbBrokerImportParser implements BrokerImportParser {

  private final XtbImportService xtbImportService;

  @Override
  public BrokerType brokerType() {
    return BrokerType.XTB;
  }

  @Override
  public ImportExecutionResult importFile(InputStream inputStream, String fileName)
      throws Exception {
    byte[] payload = readAll(inputStream);
    if (xtbImportService.isZipReport(fileName)) {
      return xtbImportService.importZip(new ByteArrayInputStream(payload), fileName);
    }

    if (xtbImportService.supports(new ByteArrayInputStream(payload))) {
      return xtbImportService.importWorkbook(new ByteArrayInputStream(payload), fileName);
    }
    throw new IllegalArgumentException(
        "Unsupported XTB statement format for V2 importer: " + fileName);
  }

  private byte[] readAll(InputStream inputStream) throws IOException {
    return inputStream.readAllBytes();
  }
}
