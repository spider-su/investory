package com.smartbox.investory.investment.port.fx;

import java.time.LocalDate;
import java.util.List;

/** Provider boundary for historical neutral FX observations. */
public interface FxRateHistoryProvider {
  List<FxRateProvider.FxHistoryQuote> fetchHistory(LocalDate from, LocalDate to);
}
