package com.smartbox.investory.integrations.importing.ibkr;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.port.importing.BrokerImportParser;
import com.smartbox.investory.investment.port.importing.BrokerImportResult;
import com.smartbox.investory.investment.port.importing.IbkrImportPort;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** IBKR file adapter; canonical ledger writes remain in investment. */
@Component
@RequiredArgsConstructor
public class IbkrBrokerImportParser implements BrokerImportParser {
  private final IbkrImportPort ibkrImportPort;

  @Override
  public ImportBroker brokerType() {
    return ImportBroker.IBKR;
  }

  @Override
  public BrokerImportResult importFile(InputStream inputStream, String fileName) throws Exception {
    return ibkrImportPort.importStatement(inputStream, fileName);
  }
}
