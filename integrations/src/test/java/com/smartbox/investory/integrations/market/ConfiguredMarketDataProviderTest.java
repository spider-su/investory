package com.smartbox.investory.integrations.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.market.twelvedata.TwelveDataMarketDataPlugin;
import com.smartbox.investory.integrations.market.twelvedata.TwelveDataService;
import com.smartbox.investory.integrations.market.yahoo.YahooFinanceService;
import com.smartbox.investory.investment.port.market.MarketQuote;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfiguredMarketDataProviderTest {
  private TwelveDataMarketDataPlugin plugin;
  private TwelveDataService twelveData;
  private YahooFinanceService yahoo;
  private IntegrationConfigurationService configuration;
  private ConfiguredMarketDataProvider provider;
  private PluginConfig config;

  @BeforeEach
  void setUp() {
    plugin = mock(TwelveDataMarketDataPlugin.class);
    twelveData = mock(TwelveDataService.class);
    yahoo = mock(YahooFinanceService.class);
    configuration = mock(IntegrationConfigurationService.class);
    provider = new ConfiguredMarketDataProvider(plugin, twelveData, yahoo, configuration);
    config = new PluginConfig(Map.of("apiKey", "secret", "baseUrl", "https://market.test"));
    when(configuration.resolveForRuntime(
            IntegrationType.MARKET_DATA, TwelveDataMarketDataPlugin.ID, PluginConfig.empty()))
        .thenReturn(config);
  }

  @Test
  void delegatesQuotesAndHistoryWithResolvedConfiguration() {
    Map<String, MarketQuote> quotes = Map.of();
    when(plugin.fetchQuotes(List.of("AAPL"), config)).thenReturn(quotes);
    NavigableMap<LocalDate, Double> daily = new TreeMap<>();
    when(twelveData.fetchDailyCloses(
            "AAPL",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            "secret",
            "https://market.test"))
        .thenReturn(daily);

    assertThat(provider.fetchQuotes(List.of("AAPL"))).isSameAs(quotes);
    assertThat(
            provider.fetchDailyCloses("AAPL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)))
        .isSameAs(daily);
  }

  @Test
  void adaptsLatestQuoteAndExternalSymbol() {
    var quote = new YahooFinanceService.YahooQuote("AAPL", "USD", LocalDate.of(2026, 1, 2), 200.0);
    when(yahoo.fetchLatestQuote("AAPL")).thenReturn(Optional.of(quote));
    when(plugin.externalSymbol("AAPL", "AAPL.US")).thenReturn("AAPL.US");

    assertThat(provider.fetchLatestQuote("AAPL")).get().extracting("currency").isEqualTo("USD");
    assertThat(provider.externalSymbol("AAPL", "AAPL.US")).isEqualTo("AAPL.US");
    verify(plugin).externalSymbol("AAPL", "AAPL.US");
  }
}
