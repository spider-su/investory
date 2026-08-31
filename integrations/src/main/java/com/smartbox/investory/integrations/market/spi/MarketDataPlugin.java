package com.smartbox.investory.integrations.market.spi;

import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import com.smartbox.investory.investment.port.market.MarketQuote;
import java.util.List;
import java.util.Map;

public interface MarketDataPlugin extends IntegrationPlugin {
  Map<String, MarketQuote> fetchQuotes(List<String> symbols, PluginConfig config);

  default String externalSymbol(String canonicalSymbol, String ticker) {
    return ticker;
  }
}
