package com.smartbox.investory.investment.api.exporting;

import java.io.IOException;

/** Public application boundary for portfolio export. */
public interface YahooPortfolioExportApi {
  void exportToYahooCsv(String filePath) throws IOException;
}
