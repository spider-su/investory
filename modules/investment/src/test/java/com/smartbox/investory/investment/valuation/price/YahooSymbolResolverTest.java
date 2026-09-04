package com.smartbox.investory.investment.valuation.price;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.investment.port.market.YahooSymbolResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Yahoo Symbol Resolver")
class YahooSymbolResolverTest {

  @DisplayName("derives Known Exchange Symbols")
  @Test
  void derivesKnownExchangeSymbols() {
    assertEquals("AAPL", YahooSymbolResolver.resolve("AAPL.US", null));
    assertEquals("ETFBW20TR.WA", YahooSymbolResolver.resolve("ETFBW20TR.PL", null));
    assertEquals("VWRA.L", YahooSymbolResolver.resolve("VWRA.UK", null));
    assertEquals("VWRL.AS", YahooSymbolResolver.resolve("VWRL.NL", null));
  }

  @DisplayName("uses Exception And Explicit Override")
  @Test
  void usesExceptionAndExplicitOverride() {
    assertEquals("BRK-B", YahooSymbolResolver.resolve("BRKB.US", null));
    assertEquals("CUSTOM", YahooSymbolResolver.resolve("AAPL.US", "CUSTOM"));
  }
}
