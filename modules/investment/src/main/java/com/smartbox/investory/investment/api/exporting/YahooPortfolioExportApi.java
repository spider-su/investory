package com.smartbox.investory.investment.api.exporting;

import java.io.IOException;

/** Public application boundary for portfolio export. */
public interface YahooPortfolioExportApi {
  void exportToYahooCsv(Long portfolioId, String filePath) throws IOException;
}
