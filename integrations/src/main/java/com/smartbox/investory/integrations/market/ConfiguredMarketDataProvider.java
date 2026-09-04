package com.smartbox.investory.integrations.market;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.market.twelvedata.TwelveDataMarketDataPlugin;
import com.smartbox.investory.integrations.market.twelvedata.TwelveDataService;
import com.smartbox.investory.integrations.market.yahoo.YahooFinanceService;
import com.smartbox.investory.investment.port.market.MarketDataProvider;
import com.smartbox.investory.investment.port.market.MarketQuote;
import com.smartbox.investory.investment.port.market.YahooSymbolResolver;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapts the configured market providers to Investment's port.
 *
 * <p>Routing is intentional: Yahoo supplies current quotes; TwelveData supplies historical
 * daily/monthly closes. Their configuration is resolved independently by the owning adapter.
 */
@Component
@RequiredArgsConstructor
public class ConfiguredMarketDataProvider implements MarketDataProvider {
  private final TwelveDataMarketDataPlugin twelveData;
  private final TwelveDataService twelveDataClient;
  private final YahooFinanceService yahooFinance;
  private final IntegrationConfigurationService configuration;

  @Override
  public Map<String, MarketQuote> fetchQuotes(List<String> symbols) {
    Map<String, MarketQuote> quotes = new LinkedHashMap<>();
    for (String symbol : symbols) {
      yahooFinance
          .fetchLatestQuote(symbol)
          .map(this::toMarketQuote)
          .ifPresent(quote -> quotes.put(symbol, quote));
    }
    return quotes;
  }

  @Override
  public NavigableMap<LocalDate, Double> fetchDailyCloses(
      String symbol, LocalDate from, LocalDate to) {
    PluginConfig config = config();
    return twelveDataClient.fetchDailyCloses(
        symbol, from, to, config.value("apiKey").orElse(""), config.value("baseUrl").orElse(null));
  }

  @Override
  public NavigableMap<String, Double> fetchMonthlyCloses(String symbol, int months) {
    PluginConfig config = config();
    return twelveDataClient.fetchMonthlyCloses(
        symbol, months, config.value("baseUrl").orElse(null));
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
    return YahooSymbolResolver.resolve(canonicalSymbol, null);
  }

  private MarketQuote toMarketQuote(YahooFinanceService.YahooQuote quote) {
    MarketQuote marketQuote = new MarketQuote();
    marketQuote.setSymbol(quote.symbol());
    marketQuote.setCurrency(quote.currency());
    marketQuote.setDatetime(quote.date().toString());
    marketQuote.setClose(quote.price());
    return marketQuote;
  }

  private PluginConfig config() {
    return configuration.resolveForRuntime(
        IntegrationType.MARKET_DATA, TwelveDataMarketDataPlugin.ID, PluginConfig.empty());
  }
}
