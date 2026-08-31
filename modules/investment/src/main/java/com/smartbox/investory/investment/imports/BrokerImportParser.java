package com.smartbox.investory.investment.imports;

import java.io.InputStream;

/** Investment-owned parser port. Broker-specific adapters implement this interface. */
public interface BrokerImportParser {
  BrokerType brokerType();

  ImportExecutionResult importFile(InputStream inputStream, String fileName) throws Exception;
}
