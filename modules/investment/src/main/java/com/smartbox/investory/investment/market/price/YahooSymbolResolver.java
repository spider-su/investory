package com.smartbox.investory.investment.market.price;

import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Resolves a Yahoo Finance symbol from the canonical asset symbol and an optional override. */
public final class YahooSymbolResolver {

  private static final Map<String, String> EXCHANGE_SUFFIXES =
      Map.ofEntries(
          Map.entry(".US", ""),
          Map.entry(".UK", ".L"),
          Map.entry(".PL", ".WA"),
          Map.entry(".FR", ".PA"),
          Map.entry(".NL", ".AS"),
          Map.entry(".ES", ".MC"),
          Map.entry(".IT", ".MI"),
          Map.entry(".SE", ".ST"),
          Map.entry(".NO", ".OL"),
          Map.entry(".FI", ".HE"),
          Map.entry(".DK", ".CO"));

  private static final Map<String, String> EXCEPTIONS = Map.of("BRKB.US", "BRK-B");

  private YahooSymbolResolver() {}

  public static String resolve(String canonicalSymbol, String override) {
    if (StringUtils.hasText(override)) {
      return override.trim();
    }
    if (!StringUtils.hasText(canonicalSymbol)) {
      return "";
    }
    String symbol = canonicalSymbol.trim();
    String exception = EXCEPTIONS.get(symbol.toUpperCase(Locale.ROOT));
    if (exception != null) {
      return exception;
    }
    for (Map.Entry<String, String> suffix : EXCHANGE_SUFFIXES.entrySet()) {
      if (symbol.toUpperCase(Locale.ROOT).endsWith(suffix.getKey())) {
        return symbol.substring(0, symbol.length() - suffix.getKey().length()) + suffix.getValue();
      }
    }
    return symbol;
  }
}
