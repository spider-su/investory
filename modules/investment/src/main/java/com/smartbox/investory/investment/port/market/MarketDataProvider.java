package com.smartbox.investory.investment.port.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

/** Outbound market-data boundary. Provider configuration remains in external adapters. */
public interface MarketDataProvider {

  Map<String, MarketQuote> fetchQuotes(List<String> symbols);

  NavigableMap<LocalDate, Double> fetchDailyCloses(String symbol, LocalDate from, LocalDate to);

  NavigableMap<String, Double> fetchMonthlyCloses(String symbol, int months);

  Optional<LatestQuote> fetchLatestQuote(String symbol);

  String externalSymbol(String canonicalSymbol, String ticker);

  record LatestQuote(String symbol, String currency, LocalDate date, double price) {}
}
