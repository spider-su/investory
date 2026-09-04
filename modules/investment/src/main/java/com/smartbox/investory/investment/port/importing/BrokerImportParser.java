package com.smartbox.investory.investment.port.importing;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import java.io.InputStream;

/** Port implemented by integration-owned broker file adapters. */
public interface BrokerImportParser {
  ImportBroker brokerType();

  BrokerImportResult importFile(InputStream inputStream, String fileName) throws Exception;
}
