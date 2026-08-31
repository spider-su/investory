package com.smartbox.investory.services.imports.xtb;

import com.smartbox.investory.infrastructure.BrokerType;
import com.smartbox.investory.services.imports.BrokerImportParser;
import com.smartbox.investory.services.imports.ImportExecutionResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XtbBrokerImportParser implements BrokerImportParser {

  private final XtbImportV2Service xtbImportV2Service;

  @Override
  public BrokerType brokerType() {
    return BrokerType.XTB;
  }

  @Override
  public ImportExecutionResult importFile(InputStream inputStream, String fileName)
      throws Exception {
    byte[] payload = readAll(inputStream);
    if (xtbImportV2Service.isZipReport(fileName)) {
      return xtbImportV2Service.importZip(new ByteArrayInputStream(payload), fileName);
    }

    if (xtbImportV2Service.supports(new ByteArrayInputStream(payload))) {
      return xtbImportV2Service.importWorkbook(new ByteArrayInputStream(payload), fileName);
    }
    throw new IllegalArgumentException(
        "Unsupported XTB statement format for V2 importer: " + fileName);
  }

  private byte[] readAll(InputStream inputStream) throws IOException {
    return inputStream.readAllBytes();
  }
}
