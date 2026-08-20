package com.smartbox.investory.investment.market.price;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class YahooSymbolResolverTest {

  @Test
  void derivesKnownExchangeSymbols() {
    assertEquals("AAPL", YahooSymbolResolver.resolve("AAPL.US", null));
    assertEquals("ETFBW20TR.WA", YahooSymbolResolver.resolve("ETFBW20TR.PL", null));
    assertEquals("VWRA.L", YahooSymbolResolver.resolve("VWRA.UK", null));
    assertEquals("VWRL.AS", YahooSymbolResolver.resolve("VWRL.NL", null));
  }

  @Test
  void usesExceptionAndExplicitOverride() {
    assertEquals("BRK-B", YahooSymbolResolver.resolve("BRKB.US", null));
    assertEquals("CUSTOM", YahooSymbolResolver.resolve("AAPL.US", "CUSTOM"));
  }
}
