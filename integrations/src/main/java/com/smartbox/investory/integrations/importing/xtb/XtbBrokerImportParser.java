package com.smartbox.investory.integrations.importing.xtb;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.port.importing.BrokerImportParser;
import com.smartbox.investory.investment.port.importing.BrokerImportResult;
import com.smartbox.investory.investment.port.importing.XtbImportPort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** XTB file adapter; canonical ledger writes remain in investment. */
@Component
@RequiredArgsConstructor
public class XtbBrokerImportParser implements BrokerImportParser {
  private final XtbImportPort xtbImportPort;

  @Override
  public ImportBroker brokerType() {
    return ImportBroker.XTB;
  }

  @Override
  public BrokerImportResult importFile(InputStream inputStream, String fileName) throws Exception {
    byte[] payload = readAll(inputStream);
    if (xtbImportPort.isZipReport(fileName)) {
      return xtbImportPort.importZip(new ByteArrayInputStream(payload), fileName);
    }
    if (xtbImportPort.supports(new ByteArrayInputStream(payload))) {
      return xtbImportPort.importWorkbook(new ByteArrayInputStream(payload), fileName);
    }
    throw new IllegalArgumentException(
        "Unsupported XTB statement format for V2 importer: " + fileName);
  }

  private byte[] readAll(InputStream inputStream) throws IOException {
    return inputStream.readAllBytes();
  }
}
