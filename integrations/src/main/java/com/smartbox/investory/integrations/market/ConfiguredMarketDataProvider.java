package com.smartbox.investory.integrations.market;

import com.smartbox.investory.integrations.infrastructure.integration.IntegrationType;
import com.smartbox.investory.integrations.infrastructure.integration.PluginConfig;
import com.smartbox.investory.integrations.infrastructure.integration.config.IntegrationConfigurationService;
import com.smartbox.investory.integrations.infrastructure.integration.market.TwelveDataMarketDataPlugin;
import com.smartbox.investory.integrations.market.client.TwelveDataService;
import com.smartbox.investory.integrations.market.client.YahooFinanceService;
import com.smartbox.investory.investment.port.market.MarketDataProvider;
import com.smartbox.investory.investment.port.market.MarketQuote;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves external provider configuration and adapts it to Investment's market-data port. */
@Component
@RequiredArgsConstructor
public class ConfiguredMarketDataProvider implements MarketDataProvider {
  private final TwelveDataMarketDataPlugin twelveData;
  private final TwelveDataService twelveDataClient;
  private final YahooFinanceService yahooFinance;
  private final IntegrationConfigurationService configuration;

  @Override
  public Map<String, MarketQuote> fetchQuotes(List<String> symbols) {
    return twelveData.fetchQuotes(symbols, config());
  }

  @Override
  public NavigableMap<LocalDate, Double> fetchDailyCloses(
      String symbol, LocalDate from, LocalDate to) {
    return twelveDataClient.fetchDailyCloses(symbol, from, to, config().value("apiKey").orElse(""));
  }

  @Override
  public NavigableMap<String, Double> fetchMonthlyCloses(String symbol, int months) {
    return twelveDataClient.fetchMonthlyCloses(symbol, months);
  }

  @Override
  public Optional<LatestQuote> fetchLatestQuote(String symbol) {
    return yahooFinance
        .fetchLatestQuote(symbol)
        .map(
            quote ->
                new LatestQuote(quote.symbol(), quote.currency(), quote.date(), quote.price()));
  }

  @Override
  public String externalSymbol(String canonicalSymbol, String ticker) {
    return twelveData.externalSymbol(canonicalSymbol, ticker);
  }

  private PluginConfig config() {
    return configuration.resolveForRuntime(
        IntegrationType.MARKET_DATA, TwelveDataMarketDataPlugin.ID, PluginConfig.empty());
  }
}
