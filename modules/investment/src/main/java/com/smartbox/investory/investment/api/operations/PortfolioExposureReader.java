package com.smartbox.investory.investment.api.operations;

import java.math.BigDecimal;
import java.util.List;

/** Base-currency symbol exposure used by concentration monitoring. */
public interface PortfolioExposureReader {
  List<SymbolExposure> symbolExposures();

  record SymbolExposure(String symbol, BigDecimal value, String currency) {}
}
