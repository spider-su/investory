package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import java.time.LocalDate;
import java.util.List;

public interface HistoricalMarketDataPort {

  List<DailyMarketBar> dailyBars(String symbol, LocalDate from, LocalDate to);
}
