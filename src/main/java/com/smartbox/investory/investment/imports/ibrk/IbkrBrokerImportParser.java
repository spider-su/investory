package com.smartbox.investory.investment.imports.ibrk;

import com.smartbox.investory.investment.imports.BrokerImportParser;
import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportExecutionResult;
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
