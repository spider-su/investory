package com.smartbox.investory.investment.port.importing;

import java.io.InputStream;

/** Investment-owned XTB import capability exposed to the provider adapter. */
public interface XtbImportPort {
  boolean isZipReport(String fileName);

  boolean supports(InputStream inputStream);

  BrokerImportResult importZip(InputStream inputStream, String sourceName) throws Exception;

  BrokerImportResult importWorkbook(InputStream inputStream, String sourceName) throws Exception;
}
