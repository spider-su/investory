package com.smartbox.investory.integrations.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.market.yahoo.YahooFinanceService;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfiguredMarketDataProviderTest {
  private YahooFinanceService yahoo;
  private ConfiguredMarketDataProvider provider;

  @BeforeEach
  void setUp() {
    yahoo = mock(YahooFinanceService.class);
    provider = new ConfiguredMarketDataProvider(yahoo);
  }

  @Test
  void delegatesQuotesAndHistoryWithResolvedConfiguration() {
    var yahooQuote =
        new YahooFinanceService.YahooQuote("AAPL", "USD", LocalDate.of(2026, 1, 2), 200.0);
    when(yahoo.fetchLatestQuote("AAPL")).thenReturn(Optional.of(yahooQuote));
    NavigableMap<LocalDate, Double> daily = new TreeMap<>();
    when(yahoo.fetchDailyCloses("AAPL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)))
        .thenReturn(daily);

    assertThat(provider.fetchQuotes(List.of("AAPL")).get("AAPL"))
        .satisfies(
            quote -> {
              assertThat(quote.getSymbol()).isEqualTo("AAPL");
              assertThat(quote.getCurrency()).isEqualTo("USD");
              assertThat(quote.getClose()).isEqualTo(200.0);
            });
    assertThat(
            provider.fetchDailyCloses("AAPL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)))
        .isSameAs(daily);
  }

  @Test
  void adaptsLatestQuoteAndExternalSymbol() {
    var quote = new YahooFinanceService.YahooQuote("AAPL", "USD", LocalDate.of(2026, 1, 2), 200.0);
    when(yahoo.fetchLatestQuote("AAPL")).thenReturn(Optional.of(quote));
    assertThat(provider.fetchLatestQuote("AAPL")).get().extracting("currency").isEqualTo("USD");
    assertThat(provider.externalSymbol("AAPL.US", "AAPL")).isEqualTo("AAPL");
  }
}
