package com.smartbox.investory.integrations.market;

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
 * <p>Yahoo Finance supplies current quotes and historical daily/monthly closes.
 */
@Component
@RequiredArgsConstructor
public class ConfiguredMarketDataProvider implements MarketDataProvider {
  private final YahooFinanceService yahooFinance;

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
    return yahooFinance.fetchDailyCloses(symbol, from, to);
  }

  @Override
  public NavigableMap<String, Double> fetchMonthlyCloses(String symbol, int months) {
    return yahooFinance.fetchMonthlyCloses(symbol, months);
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
}
