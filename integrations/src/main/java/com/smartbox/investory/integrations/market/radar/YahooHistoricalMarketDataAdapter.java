package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.integrations.market.yahoo.YahooFinanceService;
import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YahooHistoricalMarketDataAdapter implements HistoricalMarketDataPort {

  private final YahooFinanceService yahooFinance;

  @Override
  public List<DailyMarketBar> dailyBars(String symbol, LocalDate from, LocalDate to) {
    return yahooFinance.fetchDailyBars(symbol, from, to).stream()
        .map(bar -> new DailyMarketBar(
            bar.date(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()))
        .toList();
  }
}
