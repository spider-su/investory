package com.smartbox.investory.services.imports.ibrk;

import com.smartbox.investory.infrastructure.BrokerType;
import com.smartbox.investory.services.imports.BrokerImportParser;
import com.smartbox.investory.services.imports.ImportExecutionResult;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IbkrBrokerImportParser implements BrokerImportParser {

  private final IbkrImportService ibkrImportService;

  @Override
  public BrokerType brokerType() {
    return BrokerType.IBKR;
  }

  @Override
  public ImportExecutionResult importFile(InputStream inputStream, String fileName)
      throws Exception {
    return ibkrImportService.importStatement(inputStream, fileName);
  }
}
