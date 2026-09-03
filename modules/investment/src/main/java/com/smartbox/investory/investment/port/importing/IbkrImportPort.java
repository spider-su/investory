package com.smartbox.investory.investment.port.importing;

import java.io.InputStream;

/** Investment-owned IBKR import capability exposed to the provider adapter. */
public interface IbkrImportPort {
  BrokerImportResult importStatement(InputStream inputStream, String fileName) throws Exception;
}
