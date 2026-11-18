package com.smartbox.investory.rag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/** Explicit provider identifiers and parser for Alpha Vantage's single-observation quote. */
final class AlphaVantageQuotes {
  private static final ObjectMapper JSON = new ObjectMapper();

  record Listing(String providerSymbol, String currency) {}

  // Verified same-listing aliases only. Never strip suffixes from other Investory symbols.
  private static final Map<String, Listing> HOLDINGS =
      Map.of(
          "MSFT.US", new Listing("MSFT", "USD"),
          "AAPL.US", new Listing("AAPL", "USD"));

  private AlphaVantageQuotes() {}

  static Listing holding(String symbol) {
    return HOLDINGS.get(symbol);
  }

  static String providerSymbol(String symbol) {
    var listing = holding(symbol);
    return listing == null ? symbol : listing.providerSymbol();
  }

  static InvestoryRagClient.Quote parse(String data, String expectedSymbol) {
    try {
      String symbol;
      String price;
      String day;
      if (data.stripLeading().startsWith("{")) {
        var quote = JSON.readTree(data).path("Global Quote");
        symbol = quote.path("01. symbol").asString();
        price = quote.path("05. price").asString();
        day = quote.path("07. latest trading day").asString();
      } else {
        String[] lines = data.strip().split("\\R");
        if (lines.length != 2) throw new IllegalArgumentException();
        String[] keys = lines[0].split(",", -1);
        String[] values = lines[1].split(",", -1);
        if (keys.length != values.length) throw new IllegalArgumentException();
        var fields = new java.util.HashMap<String, String>();
        for (int i = 0; i < keys.length; i++) {
          String key = cell(keys[i]).toLowerCase(java.util.Locale.ROOT);
          if (fields.putIfAbsent(key, cell(values[i])) != null)
            throw new IllegalArgumentException();
        }
        symbol = fields.get("symbol");
        price = fields.get("price");
        day = fields.get("latestday");
      }
      if (!expectedSymbol.equals(symbol)
          || price == null
          || !price.matches("[0-9]{1,16}(?:\\.[0-9]{1,12})?")
          || day == null
          || !day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
      var amount = new BigDecimal(price);
      if (amount.signum() <= 0) throw new IllegalArgumentException();
      return new InvestoryRagClient.Quote(
          amount.toPlainString(),
          HOLDINGS.values().stream()
              .filter(listing -> listing.providerSymbol().equals(expectedSymbol))
              .map(Listing::currency)
              .findFirst()
              .orElse(null),
          LocalDate.parse(day));
    } catch (RuntimeException invalidQuote) {
      throw new PythonRagClient.RagUnavailableException();
    }
  }

  private static String cell(String text) {
    String trimmed = text.strip();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\""))
      return trimmed.substring(1, trimmed.length() - 1);
    return trimmed;
  }
}
